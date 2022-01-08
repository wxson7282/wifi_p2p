package com.wxson.p2p_comm

import java.io.*


object SerializableUtil {
    fun serialize(obj: Any): ByteArray? {
        val byteArrayOutputStream = ByteArrayOutputStream()
        var objectOutputStream: ObjectOutputStream? = null
        var returnValue: ByteArray? = null
        try {
            objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
            objectOutputStream.writeObject(obj)
            returnValue = byteArrayOutputStream.toByteArray()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                objectOutputStream?.close()
                byteArrayOutputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return returnValue
    }

    fun unSerialize(byteArray: ByteArray): Any? {
        val byteArrayInputStream = ByteArrayInputStream(byteArray)
        var objectInputStream: ObjectInputStream? = null
        var returnValue: Any? = null
        try {
            objectInputStream = ObjectInputStream(byteArrayInputStream)
            returnValue = objectInputStream.readObject()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        } finally {
            try {
                objectInputStream?.close()
                byteArrayInputStream.close()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        return returnValue
    }
}