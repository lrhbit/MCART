package com.axolotl.mcart.export;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * 轻量级 NBT 写入器（仅写，不读）
 * 支持 Sponge Schematic、Litematica 所需的全部 NBT 类型。
 */
public class NBTWriter implements AutoCloseable {

    private final ByteArrayOutputStream baos;
    private final DataOutput out;

    // NBT Tag类型常量
    public static final int TAG_End = 0;
    public static final int TAG_Byte = 1;
    public static final int TAG_Short = 2;
    public static final int TAG_Int = 3;
    public static final int TAG_Long = 4;
    public static final int TAG_Float = 5;
    public static final int TAG_Double = 6;
    public static final int TAG_Byte_Array = 7;
    public static final int TAG_String = 8;
    public static final int TAG_List = 9;
    public static final int TAG_Compound = 10;
    public static final int TAG_Int_Array = 11;
    public static final int TAG_Long_Array = 12;

    public NBTWriter() {
        this.baos = new ByteArrayOutputStream();
        this.out = new DataOutputStream(baos);
    }

    /** 开始写根Compound（带名） */
    public void writeRootCompound(String rootName) throws IOException {
        out.writeByte(TAG_Compound);
        writeStringPayload(rootName);
    }

    /** 结束Compound（写TAG_End） */
    public void endCompound() throws IOException {
        out.writeByte(TAG_End);
    }

    public void writeByte(String name, byte value) throws IOException {
        out.writeByte(TAG_Byte);
        writeStringPayload(name);
        out.writeByte(value);
    }

    public void writeShort(String name, short value) throws IOException {
        out.writeByte(TAG_Short);
        writeStringPayload(name);
        out.writeShort(value);
    }

    public void writeInt(String name, int value) throws IOException {
        out.writeByte(TAG_Int);
        writeStringPayload(name);
        out.writeInt(value);
    }

    public void writeLong(String name, long value) throws IOException {
        out.writeByte(TAG_Long);
        writeStringPayload(name);
        out.writeLong(value);
    }

    public void writeString(String name, String value) throws IOException {
        out.writeByte(TAG_String);
        writeStringPayload(name);
        writeStringPayload(value);
    }

    public void writeByteArray(String name, byte[] data) throws IOException {
        out.writeByte(TAG_Byte_Array);
        writeStringPayload(name);
        out.writeInt(data.length);
        out.write(data);
    }

    public void writeIntArray(String name, int[] data) throws IOException {
        out.writeByte(TAG_Int_Array);
        writeStringPayload(name);
        out.writeInt(data.length);
        for (int v : data) out.writeInt(v);
    }

    public void writeLongArray(String name, long[] data) throws IOException {
        out.writeByte(TAG_Long_Array);
        writeStringPayload(name);
        out.writeInt(data.length);
        for (long v : data) out.writeLong(v);
    }

    /** 开始一个命名的Compound */
    public void beginCompound(String name) throws IOException {
        out.writeByte(TAG_Compound);
        writeStringPayload(name);
    }

    /**
     * 开始一个 List（元素类型 + 数量），随后依次写元素。
     * @param elementType 元素 NBT tag 类型（如 TAG_Compound）
     */
    public void beginList(String name, int elementType, int count) throws IOException {
        out.writeByte(TAG_List);
        writeStringPayload(name);
        out.writeByte(elementType);
        out.writeInt(count);
    }

    /**
     * List 中 Compound 元素的开头：NBT 规定 List 元素不带类型字节、不带名称，
     * 因此这里什么都不写；元素字段写完后调用 {@link #endCompound()} 写 TAG_End。
     */
    public void beginListCompoundItem() {
        // no-op
    }

    /** 写一个空List（指定元素类型，Litematica 的空列表也要求带类型） */
    public void writeEmptyList(String name, int elementType) throws IOException {
        beginList(name, elementType, 0);
    }

    /** 兼容旧调用：空 End 类型列表 */
    public void writeEmptyList(String name) throws IOException {
        writeEmptyList(name, TAG_End);
    }

    /** 写一个Compound类型的Map（Palette等，值为Int） */
    public void writeCompoundMap(String name, Map<String, Integer> map) throws IOException {
        beginCompound(name);
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            writeInt(e.getKey(), e.getValue());
        }
        endCompound();
    }

    /** 写入字符串（UTF-8，带2字节长度前缀） */
    private void writeStringPayload(String s) throws IOException {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    /** 获取原始未压缩NBT字节 */
    public byte[] toByteArray() {
        return baos.toByteArray();
    }

    /** 获取GZip压缩后的字节 */
    public byte[] toGzippedByteArray() throws IOException {
        byte[] raw = baos.toByteArray();
        ByteArrayOutputStream gzBuffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(gzBuffer)) {
            gzip.write(raw);
        }
        return gzBuffer.toByteArray();
    }

    @Override
    public void close() throws Exception {
        if (out instanceof AutoCloseable ac) ac.close();
    }
}
