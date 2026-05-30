package com.shuli.reader.sync.transport

/**
 * 同步传输接口（T-14）
 *
 * 定义与远程存储交互的统一接口。
 * 所有路径均为相对于 rootPath 的相对路径。
 */
interface SyncTransport {

    /**
     * 读取资源内容
     * @param path 相对路径
     * @return 资源内容字节数组，不存在时返回 null
     */
    suspend fun read(path: String): ByteArray?

    /**
     * 写入资源内容
     * @param path 相对路径
     * @param data 资源内容
     * @param etag 可选的 ETag 用于乐观锁
     */
    suspend fun write(path: String, data: ByteArray, etag: String? = null)

    /**
     * 删除资源
     * @param path 相对路径
     */
    suspend fun delete(path: String)

    /**
     * 列出目录下的资源
     * @param path 相对路径
     * @return 资源信息列表
     */
    suspend fun list(path: String): List<TransportResourceInfo>

    /**
     * 检查资源是否存在
     * @param path 相对路径
     * @return 是否存在
     */
    suspend fun exists(path: String): Boolean

    /**
     * 获取资源元数据
     * @param path 相对路径
     * @return 元数据，不存在时返回 null
     */
    suspend fun getMetadata(path: String): TransportResourceMetadata?
}
