package org.mtr.mod.client;

import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.EntityExtension;
import org.mtr.mapping.registry.RegistryClient;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;

public final class WorldRenderingHelper {

	private WorldRenderingHelper() {
	}

	@Nullable
	public static Object getWorldRenderingEntity(Object worldObject) {
		if (RegistryClient.worldRenderingEntity == null) {
			return null;
		}

		final World world = wrapWorld(worldObject);
		if (world == null) {
			return null;
		}

		final EntityExtension entityExtension = RegistryClient.worldRenderingEntity.apply(world);
		return entityExtension;
	}

	@Nullable
	private static World wrapWorld(Object worldObject) {
		for (final Constructor<?> constructor : World.class.getConstructors()) {
			final Class<?>[] parameterTypes = constructor.getParameterTypes();
			if (parameterTypes.length == 1 && parameterTypes[0].isInstance(worldObject)) {
				try {
					return (World) constructor.newInstance(worldObject);
				} catch (Exception ignored) {
					return null;
				}
			}
		}

		return null;
	}
}
