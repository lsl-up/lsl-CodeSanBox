import java.util.ArrayList;
import java.util.List;

public class MemoryOverflow {

    public static void main(String[] args) {
        System.out.println("开始内存溢出测试");
        List<byte[]> memoryList = new ArrayList<>();
        
        try {
            int blockSize = 1024 * 1024; // 1MB
            int count = 0;
            
            while (true) {
                byte[] block = new byte[blockSize];
                memoryList.add(block);
                count++;
                System.out.println("已分配内存块: " + count + "MB");
                
                // 模拟一些内存使用
                for (int i = 0; i < blockSize; i++) {
                    block[i] = (byte) (i % 256);
                }
            }
        } catch (OutOfMemoryError e) {
            System.out.println("内存溢出异常: " + e.getMessage());
        }
    }
} 