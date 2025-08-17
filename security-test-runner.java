import com.fabric.watcher.archive.security.AuthenticationSecurityTest;
import com.fabric.watcher.archive.security.TokenExpirationSecurityTest;
import com.fabric.watcher.archive.security.RateLimitingSecurityTest;
import com.fabric.watcher.archive.security.AuthorizationSecurityTest;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class SecurityTestRunner {
    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                    selectClass(AuthenticationSecurityTest.class),
                    selectClass(TokenExpirationSecurityTest.class),
                    selectClass(RateLimitingSecurityTest.class),
                    selectClass(AuthorizationSecurityTest.class)
                )
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new java.io.PrintWriter(System.out));
        
        System.out.println("\n=== Security Test Execution Summary ===");
        System.out.println("Tests found: " + summary.getTestsFoundCount());
        System.out.println("Tests successful: " + summary.getTestsSucceededCount());
        System.out.println("Tests failed: " + summary.getTestsFailedCount());
        System.out.println("Tests skipped: " + summary.getTestsSkippedCount());
        
        if (summary.getTestsFailedCount() > 0) {
            System.out.println("\nFailed tests:");
            summary.getFailures().forEach(failure -> {
                System.out.println("- " + failure.getTestIdentifier().getDisplayName());
                System.out.println("  Error: " + failure.getException().getMessage());
            });
        }
    }
}