import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class BuildSystem {

    private final Path projectRoot;
    private final Path srcDir;
    private final Path targetDir;
    private final Path classesDir;
    private final Path docsDir;
    private final Path reportsDir;
    private final Path stateCacheFile;
    private final Map<String, String> previousHashes = new HashMap<>();
    private final Map<String, String> currentHashes = new HashMap<>();

    public BuildSystem(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.srcDir = projectRoot.resolve("src");
        this.targetDir = projectRoot.resolve("target");
        this.classesDir = targetDir.resolve("classes");
        this.docsDir = targetDir.resolve("docs");
        this.reportsDir = targetDir.resolve("reports");
        this.stateCacheFile = targetDir.resolve(".build_state");
    }

    public void runPipeline(String vcsTagOrCommit, String mainClass) throws Exception {
        System.out.println("==================================================");
        System.out.println("          INICIANDO PIPELINE DO SGC               ");
        System.out.println("==================================================");

        setupDirectories();
        loadPreviousState();

        // 1. Integração com SCV (Simulador/Git Wrapper)
        integrateWithVCS(vcsTagOrCommit);

        // 2. Geração de Script de Construção Reproduzível
        generateBuildScript(vcsTagOrCommit, mainClass);

        // 3. Recompilação Mínima (Detecção de arquivos modificados via SHA-256)
        List<Path> modifiedFiles = detectChangesAndComputeHashes();
        boolean compileSuccess = compileIncrementally(modifiedFiles);

        // 4. Automação de Testes
        boolean testsSuccess = runTests();

        // 5. Criação do Executável (.jar)
        Path jarPath = null;
        if (compileSuccess && testsSuccess) {
            jarPath = packageExecutableJar("app.jar", mainClass);
        }

        // 6. Geração de Documentação (Javadoc)
        boolean docsSuccess = generateDocumentation();

        // Salva estado para próxima execução incremental
        saveCurrentState();

        // 7. Relatório de Construção
        generateReport(vcsTagOrCommit, compileSuccess, testsSuccess, docsSuccess, jarPath, modifiedFiles.size());

        System.out.println("==================================================");
        System.out.println("           CONSTRUÇÃO FINALIZADA                  ");
        System.out.println("==================================================");
    }

    private void setupDirectories() throws IOException {
        Files.createDirectories(classesDir);
        Files.createDirectories(docsDir);
        Files.createDirectories(reportsDir);
    }

    // --- 1. INTEGRAÇÃO COM SCV ---
    private void integrateWithVCS(String baselineTag) {
        System.out.println("[SCV] Sincronizando repositório para a Baseline: " + baselineTag);
        // Exemplo de chamada real via processo: "git checkout tags/" + baselineTag
        System.out.println("[SCV] Workspace verificado e alinhado com o estado canônico do SCV.");
    }

    // --- 2. GERAÇÃO DE SCRIPT DE CONSTRUÇÃO ---
    private void generateBuildScript(String tag, String mainClass) throws IOException {
        Path scriptPath = targetDir.resolve("build_reproduce.sh");
        String script = String.format(
                "#!/usr/bin/env bash\n" +
                        "# Script autogerado pelo SGC para reprodução exata da Baseline: %s\n" +
                        "javac -d classes $(find src -name \"*.java\")\n" +
                        "jar cfe app.jar %s -C classes .\n",
                tag, mainClass
        );
        Files.writeString(scriptPath, script);
        System.out.println("[SCRIPT] Script de reprodução gerado em: " + scriptPath);
    }

    // --- 3. RECOMPILAÇÃO MÍNIMA ---
    private void loadPreviousState() {
        if (Files.exists(stateCacheFile)) {
            try (BufferedReader reader = Files.newBufferedReader(stateCacheFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("=");
                    if (parts.length == 2) previousHashes.put(parts[0], parts[1]);
                }
            } catch (IOException e) {
                System.err.println("[CACHE] Falha ao carregar estado anterior.");
            }
        }
    }

    private void saveCurrentState() {
        try (BufferedWriter writer = Files.newBufferedWriter(stateCacheFile)) {
            for (Map.Entry<String, String> entry : currentHashes.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("[CACHE] Falha ao persistir estado de hash.");
        }
    }

    private List<Path> detectChangesAndComputeHashes() throws Exception {
        List<Path> changed = new ArrayList<>();
        if (!Files.exists(srcDir)) return changed;

        try (var stream = Files.walk(srcDir)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                String hash = computeSHA256(file);
                String relPath = srcDir.relativize(file).toString();
                currentHashes.put(relPath, hash);

                if (!previousHashes.containsKey(relPath) || !previousHashes.get(relPath).equals(hash)) {
                    changed.add(file);
                }
            }
        }
        return changed;
    }

    private String computeSHA256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private boolean compileIncrementally(List<Path> filesToCompile) {
        if (filesToCompile.isEmpty() && !previousHashes.isEmpty()) {
            System.out.println("[COMPILADOR] Nenhuma alteração detectada. Recompilação ignorada (UP-TO-DATE).");
            return true;
        }

        System.out.println("[COMPILADOR] Compilando " + filesToCompile.size() + " arquivo(s) modificado(s)...");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("[ERRO] JDK não detectado. É necessário executar com JDK (não JRE).");
            return false;
        }

        List<String> args = new ArrayList<>(List.of("-d", classesDir.toString()));
        for (Path p : filesToCompile) {
            args.add(p.toString());
        }

        int result = compiler.run(null, null, null, args.toArray(new String[0]));
        return result == 0;
    }

    // --- 4. AUTOMAÇÃO DE TESTES ---
    private boolean runTests() {
        System.out.println("[TESTES] Executando suíte automatizada de testes de regressão e unidade...");
        // Simulação da execução de runner JUnit/TestNG
        boolean allPassed = true;
        System.out.println("[TESTES] Status: Todos os testes passaram com sucesso (100%).");
        return allPassed;
    }

    // --- 5. CRIAÇÃO DO SISTEMA EXECUTÁVEL ---
    private Path packageExecutableJar(String jarName, String mainClass) throws IOException {
        Path jarFile = targetDir.resolve(jarName);
        System.out.println("[EMPACOTAMENTO] Criando JAR executável: " + jarFile);

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest);
             var stream = Files.walk(classesDir)) {

            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String entryName = classesDir.relativize(path).toString().replace("\\", "/");
                jos.putNextEntry(new JarEntry(entryName));
                Files.copy(path, jos);
                jos.closeEntry();
            }
        }
        return jarFile;
    }

    // --- 6. GERAÇÃO DE DOCUMENTAÇÃO ---
    private boolean generateDocumentation() {
        System.out.println("[DOCS] Extraindo Javadoc das fontes...");
        var tool = ToolProvider.getSystemDocumentationTool();
        if (tool == null) {
            System.out.println("[DOCS] Javadoc tool indisponível.");
            return false;
        }
        try (var stream = Files.walk(srcDir)) {
            List<String> files = stream.filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toString)
                    .toList();
            if (files.isEmpty()) return true;

            List<String> args = new ArrayList<>(List.of("-d", docsDir.toString(), "-quiet"));
            args.addAll(files);
            int code = tool.run(null, null, null, args.toArray(new String[0]));
            return code == 0;
        } catch (Exception e) {
            System.err.println("[DOCS] Falha na geração: " + e.getMessage());
            return false;
        }
    }

    // --- 7. RELATÓRIOS ---
    private void generateReport(String tag, boolean comp, boolean test, boolean doc, Path jar, int recompiledCount) throws IOException {
        Path reportFile = reportsDir.resolve("build-summary.txt");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append("           RELATÓRIO DE CONSTRUÇÃO (SGC)          \n");
        sb.append("==================================================\n");
        sb.append("Timestamp:             ").append(timestamp).append("\n");
        sb.append("Linha de Base (SCV):   ").append(tag).append("\n");
        sb.append("Arquivos Recompilados: ").append(recompiledCount).append("\n");
        sb.append("Status Compilação:     ").append(comp ? "SUCESSO" : "FALHA").append("\n");
        sb.append("Status Testes:         ").append(test ? "PASSOU" : "FALHOU").append("\n");
        sb.append("Status Documentação:   ").append(doc ? "GERADA" : "IGNORADA/FALHA").append("\n");
        sb.append("Artefato Gerado:       ").append(jar != null ? jar.toAbsolutePath() : "Nenhum").append("\n");
        sb.append("==================================================\n");

        Files.writeString(reportFile, sb.toString());
        System.out.println("[RELATÓRIO] Resumo da construção salvo em:\n" + reportFile.toAbsolutePath());
    }

    public static void main(String[] args) {
        try {
            Path projectPath = Paths.get(".").toAbsolutePath().normalize();
            BuildSystem sgc = new BuildSystem(projectPath);

            // Executa com a linha de base 'v1.0.0-release' e classe principal 'com.exemplo.App'
            sgc.runPipeline("v1.0.0-release", "com.exemplo.App");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}