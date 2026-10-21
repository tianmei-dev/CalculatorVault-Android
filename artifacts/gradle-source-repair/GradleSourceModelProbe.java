import java.io.File;
import java.util.Map;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel;
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel;

public class GradleSourceModelProbe {
    public static void main(String[] args) {
        File installation = new File(args[0]);
        try (ProjectConnection connection = GradleConnector.newConnector()
                .forProjectDirectory(new File("D:/CalculatorVaul"))
                .useInstallation(installation).connect()) {
            KotlinDslScriptsModel model = connection.model(KotlinDslScriptsModel.class)
                .forTasks("prepareKotlinBuildScriptModel")
                .withArguments("--offline", "-Dorg.gradle.kotlin.dsl.provider.mode=strict-classpath")
                .setStandardOutput(System.out).setStandardError(System.err).get();
            int roots = 0;
            int failures = 0;
            for (Map.Entry<File, KotlinDslScriptModel> entry : model.getScriptModels().entrySet()) {
                long count = entry.getValue().getSourcePath().stream()
                    .filter(file -> file.toPath().startsWith(new File(installation, "src").toPath())).count();
                roots += (int) count;
                failures += entry.getValue().getExceptions().size();
                System.out.println(entry.getKey().getName()+": local Gradle sources="+count
                    +", script exceptions="+entry.getValue().getExceptions().size());
                if (!entry.getValue().getExceptions().isEmpty()) {
                    for (String error : entry.getValue().getExceptions()) System.out.println(error);
                }
            }
            if (roots == 0 || failures != 0) throw new IllegalStateException("Kotlin DSL source model verification failed");
            System.out.println("PASS: IDE Kotlin DSL models resolved offline using installed Gradle sources.");
        }
    }
}
