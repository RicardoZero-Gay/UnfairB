import org.objectweb.asm.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Incremental jar injector.
 *
 * Usage: java -cp asm-all.jar:. Inject <base.jar> <out.jar> <mapping|-> <classDir>
 *
 * Copies every entry of base.jar unchanged, replaces the entries produced by javac in
 * classDir and appends the ones that did not exist yet. When a mapping file is given
 * (srg-style "CL/FD/MD" lines: MCP -> SRG) the compiled classes are remapped first, so a
 * jar whose runtime uses SRG member names stays consistent.
 */
public class Inject {
    static final Map<String, String> cls = new HashMap<>();
    static final Map<String, String> fld = new HashMap<>();
    static final Map<String, String> mth = new HashMap<>();
    static final Map<String, String> superOf = new HashMap<>();
    static boolean remap;

    public static void main(String[] a) throws Exception {
        if (a.length < 4) {
            System.err.println("usage: Inject <base.jar> <out.jar> <mapping|-> <classDir>");
            System.exit(1);
        }
        String base = a[0], out = a[1], map = a[2], dir = a[3];
        if (!"-".equals(map)) {
            loadMapping(map);
            remap = true;
        }
        buildHierarchy(base);

        Path root = Paths.get(dir);
        Map<String, byte[]> injected = new LinkedHashMap<>();
        for (Path p : collect(root)) {
            String name = root.relativize(p).toString().replace(File.separatorChar, '/');
            byte[] data = Files.readAllBytes(p);
            if (remap) {
                data = remapClass(data);
                name = mappedName(name);
            }
            injected.put(name, data);
        }

        int kept = 0, replaced = 0, added = 0;
        try (JarFile jf = new JarFile(base);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(out))) {
            Set<String> written = new HashSet<>();
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                try (InputStream is = jf.getInputStream(entry)) {
                    byte[] data = is.readAllBytes();
                    if (injected.containsKey(name)) {
                        data = injected.get(name);
                        replaced++;
                    } else {
                        kept++;
                    }
                    jos.putNextEntry(new JarEntry(name));
                    jos.write(data);
                    jos.closeEntry();
                    written.add(name);
                }
            }
            for (Map.Entry<String, byte[]> e : injected.entrySet()) {
                if (written.contains(e.getKey())) {
                    continue;
                }
                jos.putNextEntry(new JarEntry(e.getKey()));
                jos.write(e.getValue());
                jos.closeEntry();
                added++;
            }
        }
        System.out.println("kept=" + kept + " replaced=" + replaced + " added=" + added + " -> " + out);
    }

    static List<Path> collect(Path root) throws Exception {
        List<Path> list = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".class")).forEach(list::add);
        }
        return list;
    }

    static void loadMapping(String file) throws Exception {
        for (String line : Files.readAllLines(Paths.get(file))) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 3) {
                continue;
            }
            switch (p[0]) {
                case "CL":
                    cls.put(p[1], p[2]);
                    break;
                case "FD":
                    fld.put(p[1], p[2]);
                    break;
                case "MD":
                    if (p.length >= 5) {
                        mth.put(p[1] + "/" + p[2] + p[3], p[4]);
                    }
                    break;
                default:
                    break;
            }
        }
        System.out.println("mapping classes=" + cls.size() + " fields=" + fld.size() + " methods=" + mth.size());
    }

    static void buildHierarchy(String base) throws Exception {
        try (JarFile jf = new JarFile(base)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream is = jf.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is.readAllBytes());
                    String[] holder = new String[2];
                    cr.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(int v, int acc, String n, String sig, String sup, String[] ifs) {
                            holder[0] = n;
                            holder[1] = sup;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (holder[0] != null && holder[1] != null) {
                        superOf.put(holder[0], holder[1]);
                    }
                }
            }
        }
    }

    static String mappedName(String entryName) {
        String cn = entryName.substring(0, entryName.length() - 6);
        String mapped = cls.getOrDefault(cn, cn);
        return mapped + ".class";
    }

    static List<String> chainOf(String c) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String cur = c;
        while (cur != null && !cur.equals("java/lang/Object") && seen.add(cur)) {
            out.add(cur);
            cur = superOf.get(cur);
            if (cur == null) {
                break;
            }
        }
        return out;
    }

    static String mapField(String owner, String name) {
        for (String c : chainOf(owner)) {
            String v = fld.get(c + "/" + name);
            if (v != null) {
                return v;
            }
        }
        return name;
    }

    static String mapMethod(String owner, String name, String desc) {
        for (String c : chainOf(owner)) {
            String v = mth.get(c + "/" + name + desc);
            if (v != null) {
                return v;
            }
        }
        return name;
    }

    static byte[] remapClass(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            String owner;

            @Override
            public void visit(int v, int acc, String n, String sig, String sup, String[] ifs) {
                owner = n;
                super.visit(v, acc, cls.getOrDefault(n, n), sig, sup == null ? null : cls.getOrDefault(sup, sup), ifs);
            }

            @Override
            public FieldVisitor visitField(int acc, String n, String d, String sig, Object val) {
                return super.visitField(acc, mapField(owner, n), d, sig, val);
            }

            @Override
            public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
                String mapped = mapMethod(owner, n, d);
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(acc, mapped, d, sig, ex)) {
                    @Override
                    public void visitMethodInsn(int op, String o, String m, String desc, boolean itf) {
                        super.visitMethodInsn(op, cls.getOrDefault(o, o), mapMethod(o, m, desc), desc, itf);
                    }

                    @Override
                    public void visitFieldInsn(int op, String o, String m, String desc) {
                        super.visitFieldInsn(op, cls.getOrDefault(o, o), mapField(o, m), desc);
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
