/**
 * BuildService — invoke jnative.jar build/run via ProcessBuilder.
 * @author Zhang Xiaoda
 */
class CoreBuildResult {
    int success;
    int exitCode;
    String stdout;
    String stderr;
    ArrayList<CoreDiag> diagnostics;

    CoreBuildResult() {
        success = 0;
        exitCode = 1;
        stdout = "";
        stderr = "";
        diagnostics = new ArrayList<CoreDiag>();
    }
}

class CoreBuild {
    String javaBin;
    String compilerJar;

    CoreBuild(String jar) {
        javaBin = "/usr/bin/java";
        compilerJar = jar;
    }

    CoreBuildResult buildProject(CoreProject project) {
        CoreBuildResult r = new CoreBuildResult();
        if (project == null) {
            r.stderr = "no project\n";
            return r;
        }
        ProcessBuilder pb = new ProcessBuilder(javaBin);
        pb.argument("-jar");
        pb.argument(compilerJar);
        pb.argument("build");
        pb.argument("--project");
        pb.argument(project.root);
        Process p = pb.start();
        if (p == null) {
            r.stderr = "failed to spawn compiler\n";
            return r;
        }
        r.stdout = p.readStdout(2 * 1024 * 1024);
        r.stderr = p.readStderr(2 * 1024 * 1024);
        r.exitCode = p.waitFor();
        if (r.exitCode == 0) {
            r.success = 1;
        }
        r.diagnostics = CoreDiagParser.parse(r.stdout + r.stderr);
        return r;
    }

    CoreBuildResult runBinary(CoreProject project) {
        CoreBuildResult r = new CoreBuildResult();
        String bin = project.binaryPath();
        if (!File.exists(bin)) {
            r.stderr = "binary missing: " + bin + "\n";
            return r;
        }
        ProcessBuilder pb = new ProcessBuilder(bin);
        pb.directory(project.root);
        Process p = pb.start();
        if (p == null) {
            r.stderr = "failed to start process\n";
            return r;
        }
        r.stdout = p.readStdout(2 * 1024 * 1024);
        r.stderr = p.readStderr(2 * 1024 * 1024);
        r.exitCode = p.waitFor();
        if (r.exitCode == 0) {
            r.success = 1;
        }
        return r;
    }
}
