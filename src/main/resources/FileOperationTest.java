import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileOperationTest {

    public static void main(String[] args) {
        System.out.println("开始文件操作测试");
        
        try {
            // 尝试创建文件
            File file = new File("/tmp/test.txt");
            FileWriter writer = new FileWriter(file);
            writer.write("这是一个测试文件");
            writer.close();
            
            System.out.println("文件创建成功: " + file.getAbsolutePath());
            
            // 尝试删除文件
            if (file.delete()) {
                System.out.println("文件删除成功");
            } else {
                System.out.println("文件删除失败");
            }
            
        } catch (IOException e) {
            System.out.println("文件操作异常: " + e.getMessage());
        } catch (SecurityException e) {
            System.out.println("安全异常: " + e.getMessage());
        }
        
        System.out.println("文件操作测试完成");
    }
} 