package com.minicat.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES 加密工具类
 * 用于加密和解密数据库连接密码
 * 
 * 功能说明：
 * 1. 使用 AES-128 算法进行加密
 * 2. 加密后的密码以 "ENC()" 格式存储
 * 3. 支持自动识别加密和明文密码
 */
@Slf4j
@Component
public class EncryptionUtil {
    
    // AES 加密算法
    private static final String ALGORITHM = "AES";
    
    // 加密密钥长度（128位）
    private static final int KEY_SIZE = 128;
    
    // 加密前缀标识
    private static final String ENCRYPTED_PREFIX = "ENC(";
    private static final String ENCRYPTED_SUFFIX = ")";
    
    // 默认加密密钥（实际使用时应该从配置文件读取）
    @Value("${minicat.security.encryption-key:minicat-default-secret-key-2024}")
    private String encryptionKey;
    
    /**
     * 加密密码
     * 
     * @param plainText 明文密码
     * @return 加密后的密码，格式为 ENC(base64编码)
     */
    public String encrypt(String plainText) {
        // 如果为空或已加密，直接返回
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        if (isEncrypted(plainText)) {
            log.info("密码已加密，无需重复加密");
            return plainText;
        }
        
        try {
            // 创建密钥
            SecretKey secretKey = generateKey();
            
            // 创建加密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            // 执行加密
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // Base64 编码
            String base64Encoded = Base64.getEncoder().encodeToString(encryptedBytes);
            
            // 添加前缀和后缀
            String result = ENCRYPTED_PREFIX + base64Encoded + ENCRYPTED_SUFFIX;
            
            log.info("密码加密成功");
            return result;
            
        } catch (Exception e) {
            log.error("密码加密失败", e);
            throw new RuntimeException("密码加密失败", e);
        }
    }
    
    /**
     * 解密密码
     * 
     * @param encryptedText 加密的密码，格式为 ENC(base64编码)
     * @return 解密后的明文密码
     */
    public String decrypt(String encryptedText) {
        // 如果为空，直接返回
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        // 如果不是加密格式，直接返回（兼容明文密码）
        if (!isEncrypted(encryptedText)) {
            log.warn("密码未加密，建议使用加密存储");
            return encryptedText;
        }
        
        try {
            // 提取 Base64 编码的密文
            String base64Encoded = extractEncryptedContent(encryptedText);
            
            // Base64 解码
            byte[] encryptedBytes = Base64.getDecoder().decode(base64Encoded);
            
            // 创建密钥
            SecretKey secretKey = generateKey();
            
            // 创建解密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            // 执行解密
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            
            String result = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            log.info("密码解密成功");
            return result;
            
        } catch (Exception e) {
            log.error("密码解密失败", e);
            throw new RuntimeException("密码解密失败", e);
        }
    }
    
    /**
     * 判断密码是否已加密
     * 
     * @param text 待判断的文本
     * @return true 表示已加密，false 表示未加密
     */
    public boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.startsWith(ENCRYPTED_PREFIX) && text.endsWith(ENCRYPTED_SUFFIX);
    }
    
    /**
     * 提取加密内容（去除前缀和后缀）
     * 
     * @param encryptedText 加密文本
     * @return Base64 编码的密文
     */
    private String extractEncryptedContent(String encryptedText) {
        if (!isEncrypted(encryptedText)) {
            return encryptedText;
        }
        
        // 去除 "ENC(" 和 ")"
        return encryptedText.substring(
            ENCRYPTED_PREFIX.length(), 
            encryptedText.length() - ENCRYPTED_SUFFIX.length()
        );
    }
    
    /**
     * 生成 AES 密钥
     * 
     * @return SecretKey 对象
     */
    private SecretKey generateKey() throws Exception {
        // 使用固定的密钥字符串生成密钥
        // 确保密钥长度为 16 字节（128位）
        byte[] keyBytes = new byte[16];
        byte[] sourceBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        
        // 将源字节复制到密钥字节数组（如果不足16字节则填充，超过则截断）
        System.arraycopy(
            sourceBytes, 
            0, 
            keyBytes, 
            0, 
            Math.min(sourceBytes.length, keyBytes.length)
        );
        
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
    
    /**
     * 生成随机密钥（用于初始化或重置密钥）
     * 
     * @return Base64 编码的密钥字符串
     */
    public String generateRandomKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("生成随机密钥失败", e);
            throw new RuntimeException("生成随机密钥失败", e);
        }
    }
}

