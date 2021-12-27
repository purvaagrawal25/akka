/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.cluster.sharding.typed.internal

import java.io.NotSerializableException

import akka.actor.typed.EntityEnvelope
import akka.annotation.InternalApi
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.internal.protobuf.ShardingMessages
import akka.remote.serialization.WrappedPayloadSupport
import akka.serialization.BaseSerializer
import akka.serialization.SerializerWithStringManifest

/**
 * INTERNAL API
 */
@InternalApi private[akka] class ShardingSerializer(val system: akka.actor.ExtendedActorSystem)
    extends SerializerWithStringManifest
    with BaseSerializer {

  private val payloadSupport = new WrappedPayloadSupport(system)

  private val ShardingEnvelopeManifest = "a"
  private val EntityEnvelopeManifest = "b"
  private val StartEntityManifest = "c"

  override def manifest(o: AnyRef): String = o match {
    case _: ShardingEnvelope[_]        => ShardingEnvelopeManifest
    case _: EntityEnvelope[_]          => EntityEnvelopeManifest
    case _: EntityEnvelope.StartEntity => StartEntityManifest
    case _ =>
      throw new IllegalArgumentException(s"Can't serialize object of type ${o.getClass} in [${getClass.getName}]")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case env: ShardingEnvelope[_] =>
      val builder = ShardingMessages.ShardingEnvelope.newBuilder()
      builder.setEntityId(env.entityId)
      builder.setMessage(payloadSupport.payloadBuilder(env.message))
      builder.build().toByteArray

    case env: EntityEnvelope[_] =>
      val builder = ShardingMessages.EntityEnvelope.newBuilder()
      builder.setEntityId(env.entityId)
      builder.setMessage(payloadSupport.payloadBuilder(env.message))
      builder.build().toByteArray

    case startEntity: EntityEnvelope.StartEntity =>
      val builder = ShardingMessages.StartEntity.newBuilder()
      builder.setEntityId(startEntity.entityId)
      builder.build().toByteArray

    case _ =>
      throw new IllegalArgumentException(s"Cannot serialize object of type [${o.getClass.getName}]")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case ShardingEnvelopeManifest =>
      val env = ShardingMessages.ShardingEnvelope.parseFrom(bytes)
      val entityId = env.getEntityId
      val wrappedMsg = payloadSupport.deserializePayload(env.getMessage)
      ShardingEnvelope(entityId, wrappedMsg)

    case EntityEnvelopeManifest =>
      val env = ShardingMessages.EntityEnvelope.parseFrom(bytes)
      val entityId = env.getEntityId
      val wrappedMsg = payloadSupport.deserializePayload(env.getMessage)
      EntityEnvelope(entityId, wrappedMsg)

    case StartEntityManifest =>
      val msg = ShardingMessages.StartEntity.parseFrom(bytes)
      val entityId = msg.getEntityId
      new EntityEnvelope.StartEntity(entityId)

    case _ =>
      throw new NotSerializableException(
        s"Unimplemented deserialization of message with manifest [$manifest] in [${getClass.getName}]")
  }

}
