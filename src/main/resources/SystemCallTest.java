import java.io.IOException;

public class SystemCallTest {

    public static void main(String[] args) {
        System.out.println("开始系统调用测试");
        
        try {
            // 尝试执行系统命令
            Process process = Runtime.getRuntime().exec("ls -la");
            int exitCode = process.waitFor();
            System.out.println("系统命令执行成功，退出码: " + exitCode);
            
        } catch (IOException e) {
            System.out.println("系统命令执行失败: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("系统命令执行中断: " + e.getMessage());
        } catch (SecurityException e) {
            System.out.println("安全异常: " + e.getMessage());
        }
        
        try {
            // 尝试获取系统属性
            String osName = System.getProperty("os.name");
            String javaVersion = System.getProperty("java.version");
            System.out.println("操作系统: " + osName);
            System.out.println("Java版本: " + javaVersion);
            
        } catch (SecurityException e) {
            System.out.println("获取系统属性失败: " + e.getMessage());
        }
        
        System.out.println("系统调用测试完成");
    }
} 