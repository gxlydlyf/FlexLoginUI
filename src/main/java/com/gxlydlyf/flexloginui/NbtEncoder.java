package com.gxlydlyf.flexloginui;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class NbtEncoder {

    // ---------- VarInt ----------
    public static void writeVarInt(ByteBuf buf, int value) {
        do {
            int temp = value & 0x7F;
            value >>>= 7;
            if (value != 0) temp |= 0x80;
            buf.writeByte(temp);
        } while (value != 0);
    }

    // ---------- 基本大端写入 ----------
    public static void writeUShort(ByteBuf buf, int value) {
        buf.writeShort(value & 0xFFFF);
    }

    public static void writeShort(ByteBuf buf, int value) {
        buf.writeShort(value);
    }

    public static void writeInt(ByteBuf buf, int value) {
        buf.writeInt(value);
    }

    public static void writeLong(ByteBuf buf, long value) {
        buf.writeLong(value);
    }

    public static void writeFloat(ByteBuf buf, float value) {
        buf.writeFloat(value);
    }

    public static void writeDouble(ByteBuf buf, double value) {
        buf.writeDouble(value);
    }

    public static void writeBoolean(ByteBuf buf, boolean value) {
        buf.writeByte(value ? 0x01 : 0x00);
    }

    // ---------- 字符串负载 (2字节无符号长度 + UTF8) ----------
    public static void writeStringPayload(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeUShort(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    // ---------- 单标签: ID (1字节) + 名称 (2字节长度+UTF8) + 负载 ----------
    public static void startTag(ByteBuf buf, int tagId, String name) {
        buf.writeByte(tagId);
        writeStringPayload(buf, name);
    }

    // ---------- 各种类型标签的负载写入 ----------
    public static void writeBytePayload(ByteBuf buf, int value) {
        buf.writeByte(value);
    }

    public static void writeShortPayload(ByteBuf buf, int value) {
        writeShort(buf, value);
    }

    public static void writeIntPayload(ByteBuf buf, int value) {
        writeInt(buf, value);
    }

    public static void writeLongPayload(ByteBuf buf, long value) {
        writeLong(buf, value);
    }

    public static void writeFloatPayload(ByteBuf buf, float value) {
        writeFloat(buf, value);
    }

    public static void writeDoublePayload(ByteBuf buf, double value) {
        writeDouble(buf, value);
    }

    /** 复合标签负载: 子标签序列 + 0x00 结束 */
    public static void writeCompoundPayload(ByteBuf buf, List<Runnable> childrenWriters) {
        for (Runnable w : childrenWriters) w.run();
        buf.writeByte(0x00);
    }

    /** 列表负载: 子标签ID (1字节) + 4字节有符号长度 + 子负载序列 */
    public static void writeListPayload(ByteBuf buf, int childTagId, List<Runnable> childPayloadWriters) {
        buf.writeByte(childTagId);
        writeInt(buf, childPayloadWriters.size());
        for (Runnable w : childPayloadWriters) w.run();
    }

    // ---------- 高级封装: 直接写完整命名标签 ----------
    public static void tagByte(ByteBuf buf, String name, int value) {
        startTag(buf, 1, name);
        writeBytePayload(buf, value);
    }

    public static void tagShort(ByteBuf buf, String name, int value) {
        startTag(buf, 2, name);
        writeShortPayload(buf, value);
    }

    public static void tagInt(ByteBuf buf, String name, int value) {
        startTag(buf, 3, name);
        writeIntPayload(buf, value);
    }

    public static void tagLong(ByteBuf buf, String name, long value) {
        startTag(buf, 4, name);
        writeLongPayload(buf, value);
    }

    public static void tagFloat(ByteBuf buf, String name, float value) {
        startTag(buf, 5, name);
        writeFloatPayload(buf, value);
    }

    public static void tagDouble(ByteBuf buf, String name, double value) {
        startTag(buf, 6, name);
        writeDoublePayload(buf, value);
    }

    public static void tagString(ByteBuf buf, String name, String value) {
        startTag(buf, 8, name);
        writeStringPayload(buf, value);
    }

    public static void tagCompound(ByteBuf buf, String name, List<Runnable> children) {
        startTag(buf, 10, name);
        writeCompoundPayload(buf, children);
    }

    public static void tagList(ByteBuf buf, String name, int childTagId, List<Runnable> childPayloads) {
        startTag(buf, 9, name);
        writeListPayload(buf, childTagId, childPayloads);
    }

    public static void tagBoolean(ByteBuf buf, String name, boolean value) {
        startTag(buf, 1, name);
        writeBoolean(buf, value);
    }

    /** 根复合标签头: VarInt(0) + 0x0A */
    public static void writeRootCompoundStart(ByteBuf buf) {
        writeVarInt(buf, 0);   // 名称长度 VarInt 0
        buf.writeByte(0x0A);   // 复合标签 ID
    }
}