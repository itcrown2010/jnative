/**
 * FileSystemService for Studio Core (CLI).
 * @author Zhang Xiaoda
 */
class CoreFs {
    static boolean exists(String path) {
        return File.exists(path);
    }

    static boolean isDirectory(String path) {
        return Files.isDirectory(FsPath.of(path));
    }

    static String readFile(String path) {
        return File.readString(path);
    }

    static void writeFile(String path, String text) {
        File.writeString(path, text);
    }

    static ArrayList<String> listDirectory(String path) {
        return Files.list(FsPath.of(path));
    }

    static void printTree(String root, String rel, int depth, int maxDepth) {
        if (depth > maxDepth) {
            return;
        }
        String path = root;
        if (rel.length() > 0) {
            path = root + "/" + rel;
        }
        ArrayList<String> names = Files.list(FsPath.of(path));
        int i = 0;
        while (i < names.size()) {
            String name = names.get(i);
            if (!name.equals(".") && !name.equals("..") && !name.equals(".git")
                    && !name.equals("build") && !name.equals(".jnative")) {
                String childRel = name;
                if (rel.length() > 0) {
                    childRel = rel + "/" + name;
                }
                String indent = "";
                int d = 0;
                while (d < depth) {
                    indent = indent + "  ";
                    d = d + 1;
                }
                String full = root + "/" + childRel;
                if (Files.isDirectory(FsPath.of(full))) {
                    System.out.println(indent + childRel + "/");
                    printTree(root, childRel, depth + 1, maxDepth);
                } else {
                    System.out.println(indent + childRel);
                }
            }
            i = i + 1;
        }
    }
}
