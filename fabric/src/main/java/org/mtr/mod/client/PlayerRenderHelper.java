package org.mtr.mod.client;

import org.mtr.mapping.mapper.EntityHelper;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PlayerRenderHelper {

	private PlayerRenderHelper() {
	}

	public static boolean shouldHidePlayer(Object playerObject) {
		final UUID uuid = getUuid(playerObject);
		return uuid != null && EntityHelper.HIDDEN_PLAYERS.stream().anyMatch(hiddenUuid -> hiddenUuid.equals(uuid));
	}

	private static UUID getUuid(Object playerObject) {
		if (playerObject == null) {
			return null;
		}

		try {
			final Method getUuidMethod = playerObject.getClass().getMethod("getUuid");
			final Object uuidObject = getUuidMethod.invoke(playerObject);
			return uuidObject instanceof UUID ? (UUID) uuidObject : null;
		} catch (Exception ignored) {
		}

		try {
			final Method getUuidMethod = playerObject.getClass().getMethod("getUUID");
			final Object uuidObject = getUuidMethod.invoke(playerObject);
			return uuidObject instanceof UUID ? (UUID) uuidObject : null;
		} catch (Exception ignored) {
			return null;
		}
	}
}
