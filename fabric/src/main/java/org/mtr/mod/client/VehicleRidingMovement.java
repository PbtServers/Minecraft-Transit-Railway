package org.mtr.mod.client;

import org.apache.commons.lang3.StringUtils;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.EntityHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.KeyBindings;
import org.mtr.mod.generated.lang.TranslationProvider;
import org.mtr.mod.item.ItemDepotDriverKey;
import org.mtr.mod.item.ItemDriverKey;
import org.mtr.mod.packet.PacketUpdateVehicleRidingEntities;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.render.RenderVehicleHelper;
import org.mtr.mod.screen.LiftSelectionScreen;

import javax.annotation.Nullable;
import java.util.Comparator;

public class VehicleRidingMovement {

	private static long ridingDepotId;
	private static long ridingSidingId;
	private static long ridingVehicleId;
	private static int ridingVehicleCarNumber;
	private static double ridingVehicleX;
	private static double ridingVehicleY;
	private static double ridingVehicleZ;
	private static boolean isOnGangway;
	private static int ridingVehicleCooldown;
	private static float shiftHoldingTicks;

	private static int ridingVehicleCarNumberCacheOld;
	private static Vector3d ridingPositionCacheOld;
	private static Vector3d ridingPositionCache;
	private static Double ridingYawDifference;
	private static double previousVehicleYaw;

	private static long sendPositionUpdateTime;

	private static boolean isHoldingDriverKey = false;
	private static int pressingAccelerateTicks = 0;
	private static int pressingBrakeTicks = 0;
	private static int pressingDoorsTicks = 0;
	private static int pressingAtoTicks = 0;
	private static int doorOverrideTicks;
	private static boolean seatToggleRequested;
	private static boolean seatToggleKeyPressedLastTick;
	private static boolean seatUseRequested;
	private static boolean isSeated;
	private static Vector3d seatedPosition;

	public static final int SEND_UPDATE_FREQUENCY = 1000;
	private static final float VEHICLE_WALKING_SPEED_MULTIPLIER = 0.005F;
	private static final int RIDING_COOLDOWN = 5;
	private static final int SHIFT_ACTIVATE_TICKS = 30;
	private static final int DISMOUNT_PROGRESS_BAR_LENGTH = 30;

	public static void tick() {
		final MinecraftClient minecraftClient = MinecraftClient.getInstance();
		final ItemDriverKey driverKey = getValidHoldingKey(ridingDepotId);
		final boolean seatToggleKeyPressed = KeyBindings.TRAIN_TOGGLE_SITTING.isPressed();
		final boolean sneakKeyPressed = minecraftClient.getOptionsMapped().getKeySneakMapped().isPressed();

		if (seatToggleKeyPressed && !seatToggleKeyPressedLastTick && ridingVehicleId != 0) {
			seatToggleRequested = true;
		}
		seatToggleKeyPressedLastTick = seatToggleKeyPressed;

		// Click derecho desactivado temporalmente. Antes sentaba al jugador al hacer click en cualquier parte del vehículo.
		seatUseRequested = false;

		if (ridingVehicleId != 0) {
			if (ridingVehicleCooldown < RIDING_COOLDOWN && shiftHoldingTicks < SHIFT_ACTIVATE_TICKS) {
				ridingVehicleCooldown++;
			} else {
				sendUpdate(true);
				ridingDepotId = 0;
				ridingSidingId = 0;
				ridingVehicleId = 0;
				resetSeatingState();
			}
		}

		if (ridingPositionCache != null) {
			ridingVehicleCarNumberCacheOld = ridingVehicleCarNumber;
			ridingPositionCacheOld = ridingPositionCache;
		}

		final boolean isHoldingDriverKeyNew = driverKey != null;
		pressingAccelerateTicks = isHoldingDriverKeyNew && driverKey.canDrive && KeyBindings.TRAIN_ACCELERATE.isPressed() ? pressingAccelerateTicks + 1 : 0;
		pressingBrakeTicks = isHoldingDriverKeyNew && driverKey.canDrive && KeyBindings.TRAIN_BRAKE.isPressed() ? pressingBrakeTicks + 1 : 0;
		pressingDoorsTicks = isHoldingDriverKeyNew && driverKey.canOpenDoors && KeyBindings.TRAIN_TOGGLE_DOORS.isPressed() ? pressingDoorsTicks + 1 : 0;
		pressingAtoTicks = isHoldingDriverKeyNew && driverKey.canDrive && KeyBindings.TRAIN_TOGGLE_DOORS.isPressed() ? pressingAtoTicks + 1 : 0;

		if (sendPositionUpdateTime > 0 && sendPositionUpdateTime <= System.currentTimeMillis() || isHoldingDriverKeyNew != isHoldingDriverKey || pressingAccelerateTicks == 1 || pressingBrakeTicks == 1 || pressingDoorsTicks == 1 || pressingAtoTicks == 1 || doorOverrideTicks == 1) {
			isHoldingDriverKey = isHoldingDriverKeyNew;
			sendUpdate(false);
		}

		if (doorOverrideTicks > 0) {
			doorOverrideTicks--;
		}

		if (ridingVehicleId == 0) {
			resetSeatingState();

			final ClientPlayerEntity clientPlayerEntity = minecraftClient.getPlayerMapped();
			if (clientPlayerEntity != null) {
				clientPlayerEntity.setPose(EntityPose.STANDING);
			}
		} else {
			if (KeyBindings.LIFT_MENU.isPressed()) {
				final Screen currentScreen = minecraftClient.getCurrentScreenMapped();
				if (MinecraftClientData.getLift(ridingVehicleId) != null && (currentScreen == null || !(currentScreen.data instanceof LiftSelectionScreen))) {
					minecraftClient.openScreen(new Screen(new LiftSelectionScreen(ridingVehicleId)));
				}
			}

			final ClientPlayerEntity clientPlayerEntity = minecraftClient.getPlayerMapped();
			if (clientPlayerEntity != null && sneakKeyPressed) {
				shiftHoldingTicks += minecraftClient.getLastFrameDuration();
			} else {
				shiftHoldingTicks = 0;
			}

			if (clientPlayerEntity != null) {
				clientPlayerEntity.setPose(isSeated ? EntityPose.CROUCHING : EntityPose.STANDING);
			}
		}
	}

	public static void startRiding(ObjectArrayList<Box> openFloorsAndDoorways, long depotId, long sidingId, long vehicleId, int carNumber, double x, double y, double z, double yaw) {
		if (ridingVehicleId == 0 || isRiding(vehicleId)) {
			for (final Box floorOrDoorway : openFloorsAndDoorways) {
				if (RenderVehicleHelper.boxContains(floorOrDoorway, x, y, z)) {
					ridingDepotId = depotId;
					ridingSidingId = sidingId;
					ridingVehicleId = vehicleId;
					ridingVehicleCarNumber = carNumber;
					ridingVehicleX = x;
					ridingVehicleY = y;
					ridingVehicleZ = z;
					isOnGangway = false;
					ridingPositionCacheOld = null;
					ridingPositionCache = null;
					ridingYawDifference = null;
					previousVehicleYaw = yaw;
					if (ridingVehicleId == 0) {
						sendUpdate(false);
					}
				}
			}
		}
	}

	public static void movePlayer(
			long millisElapsed, long vehicleId, int carNumber,
			ObjectArrayList<ObjectBooleanImmutablePair<Box>> floorsAndDoorways,
			@Nullable GangwayMovementPositions previousCarGangwayMovementPositions,
			@Nullable GangwayMovementPositions thisCarGangwayMovementPositions1,
			@Nullable GangwayMovementPositions thisCarGangwayMovementPositions2,
			PositionAndRotation positionAndRotation
	) {
		final ClientPlayerEntity clientPlayerEntity = MinecraftClient.getInstance().getPlayerMapped();
		if (clientPlayerEntity == null) {
			return;
		}

		if (isRiding(vehicleId) && ridingVehicleCarNumber == carNumber) {
			ridingVehicleCooldown = 0;
			final double entityYawOld = EntityHelper.getYaw(new Entity(clientPlayerEntity.data));

			if (isSeated && seatedPosition != null) {
				ridingVehicleX = seatedPosition.getXMapped();
				ridingVehicleY = seatedPosition.getYMapped();
				ridingVehicleZ = seatedPosition.getZMapped();
				ridingPositionCache = seatedPosition;

				final Vector3d newPlayerPosition = positionAndRotation.transformForwards(seatedPosition, Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
				movePlayer(newPlayerPosition.getXMapped(), newPlayerPosition.getYMapped(), newPlayerPosition.getZMapped());

				EntityHelper.setYaw(new Entity(clientPlayerEntity.data), (float) (Math.toDegrees(previousVehicleYaw - positionAndRotation.yaw) + entityYawOld));
				ridingYawDifference = Math.abs(positionAndRotation.yaw - previousVehicleYaw) > 0.001 ? previousVehicleYaw + Math.toRadians(entityYawOld) : null;
				previousVehicleYaw = positionAndRotation.yaw;
				return;
			}

			final float speedMultiplier = millisElapsed * VEHICLE_WALKING_SPEED_MULTIPLIER * (clientPlayerEntity.isSprinting() ? 2 : 1);

			final Vector3d movement = positionAndRotation.transformBackwards(new Vector3d(
					Math.abs(clientPlayerEntity.getSidewaysSpeedMapped()) > 0.5 ? Math.copySign(speedMultiplier, clientPlayerEntity.getSidewaysSpeedMapped()) : 0,
					0,
					Math.abs(clientPlayerEntity.getForwardSpeedMapped()) > 0.5 ? Math.copySign(speedMultiplier, clientPlayerEntity.getForwardSpeedMapped()) : 0
			), (vector, pitch) -> vector, (vector, yaw) -> vector.rotateY((float) (yaw - Math.toRadians(entityYawOld))), (vector, x, y, z) -> vector);

			final double movementX = movement.getXMapped();
			final double movementZ = movement.getZMapped();

			if (sendPositionUpdateTime == 0 && (movementX != 0 || movementZ != 0)) {
				sendPositionUpdateTime = System.currentTimeMillis() + SEND_UPDATE_FREQUENCY;
			}

			if (isOnGangway) {
				if (thisCarGangwayMovementPositions1 == null || previousCarGangwayMovementPositions == null) {
					sendUpdate(true);
					ridingDepotId = 0;
					ridingSidingId = 0;
					ridingVehicleId = 0;
					resetSeatingState();
				} else {
					if (ridingVehicleZ + movementZ > 1) {
						isOnGangway = false;
						ridingVehicleX = thisCarGangwayMovementPositions1.getX(ridingVehicleX);
						ridingVehicleZ = thisCarGangwayMovementPositions1.getZ() + ridingVehicleZ + movementZ - 1;
						ridingPositionCache = null;
					} else if (ridingVehicleZ + movementZ < 0) {
						isOnGangway = false;
						ridingVehicleCarNumber--;
						ridingVehicleX = previousCarGangwayMovementPositions.getX(ridingVehicleX);
						ridingVehicleZ = previousCarGangwayMovementPositions.getZ() + ridingVehicleZ + movementZ;
						ridingPositionCache = null;
					} else {
						ridingVehicleX = Utilities.clamp(ridingVehicleX + movementX, 0, 1);
						ridingVehicleZ += movementZ;

						final Vector3d position1Min = previousCarGangwayMovementPositions.getMinWorldPosition();
						final Vector3d position1Max = previousCarGangwayMovementPositions.getMaxWorldPosition();
						final Vector3d position2Min = thisCarGangwayMovementPositions1.getMinWorldPosition();
						final Vector3d position2Max = thisCarGangwayMovementPositions1.getMaxWorldPosition();

						final double positionX = getFromScale(
								getFromScale(position1Min.getXMapped(), position1Max.getXMapped(), ridingVehicleX),
								getFromScale(position2Min.getXMapped(), position2Max.getXMapped(), ridingVehicleX),
								ridingVehicleZ
						);
						final double positionY = getFromScale(
								getFromScale(position1Min.getYMapped(), position1Max.getYMapped(), ridingVehicleX),
								getFromScale(position2Min.getYMapped(), position2Max.getYMapped(), ridingVehicleX),
								ridingVehicleZ
						);
						final double positionZ = getFromScale(
								getFromScale(position1Min.getZMapped(), position1Max.getZMapped(), ridingVehicleX),
								getFromScale(position2Min.getZMapped(), position2Max.getZMapped(), ridingVehicleX),
								ridingVehicleZ
						);

						ridingPositionCache = positionAndRotation.transformBackwards(new Vector3d(positionX, positionY, positionZ), Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);
						movePlayer(positionX, positionY, positionZ);
					}
				}
			} else {
				if (thisCarGangwayMovementPositions1 != null && thisCarGangwayMovementPositions1.getPercentageZ(ridingVehicleZ + movementZ) < 1) {
					isOnGangway = true;
					ridingVehicleX = thisCarGangwayMovementPositions1.getPercentageX(ridingVehicleX + movementX);
					ridingVehicleZ = thisCarGangwayMovementPositions1.getPercentageZ(ridingVehicleZ + movementZ);
					ridingPositionCache = null;
				} else if (thisCarGangwayMovementPositions2 != null && thisCarGangwayMovementPositions2.getPercentageZ(ridingVehicleZ + movementZ) > 0) {
					isOnGangway = true;
					ridingVehicleCarNumber++;
					ridingVehicleX = thisCarGangwayMovementPositions2.getPercentageX(ridingVehicleX + movementX);
					ridingVehicleZ = thisCarGangwayMovementPositions2.getPercentageZ(ridingVehicleZ + movementZ);
					ridingPositionCache = null;
				} else {
					final ObjectArrayList<Vector3d> offsets = new ObjectArrayList<>();

					clampPosition(floorsAndDoorways, ridingVehicleX + movementX - RenderVehicleHelper.HALF_PLAYER_WIDTH, ridingVehicleZ + movementZ - RenderVehicleHelper.HALF_PLAYER_WIDTH, offsets);
					clampPosition(floorsAndDoorways, ridingVehicleX + movementX + RenderVehicleHelper.HALF_PLAYER_WIDTH, ridingVehicleZ + movementZ - RenderVehicleHelper.HALF_PLAYER_WIDTH, offsets);
					clampPosition(floorsAndDoorways, ridingVehicleX + movementX + RenderVehicleHelper.HALF_PLAYER_WIDTH, ridingVehicleZ + movementZ + RenderVehicleHelper.HALF_PLAYER_WIDTH, offsets);
					clampPosition(floorsAndDoorways, ridingVehicleX + movementX - RenderVehicleHelper.HALF_PLAYER_WIDTH, ridingVehicleZ + movementZ + RenderVehicleHelper.HALF_PLAYER_WIDTH, offsets);

					if (offsets.isEmpty()) {
						sendUpdate(true);
						ridingDepotId = 0;
						ridingSidingId = 0;
						ridingVehicleId = 0;
						resetSeatingState();
					} else {
						double clampX = 0;
						double maxY = -Double.MAX_VALUE;
						double clampZ = 0;

						for (final Vector3d offset : offsets) {
							if (Math.abs(offset.getXMapped()) > Math.abs(clampX)) {
								clampX = offset.getXMapped();
							}
							maxY = Math.max(maxY, offset.getYMapped());
							if (Math.abs(offset.getZMapped()) > Math.abs(clampZ)) {
								clampZ = offset.getZMapped();
							}
						}

						ridingVehicleX += movementX + clampX;
						ridingVehicleY = maxY;
						ridingVehicleZ += movementZ + clampZ;
					}

					ridingPositionCache = new Vector3d(ridingVehicleX, ridingVehicleY, ridingVehicleZ);
					final Vector3d newPlayerPosition = positionAndRotation.transformForwards(ridingPositionCache, Vector3d::rotateX, Vector3d::rotateY, Vector3d::add);

					movePlayer(newPlayerPosition.getXMapped(), newPlayerPosition.getYMapped(), newPlayerPosition.getZMapped());
					EntityHelper.setYaw(new Entity(clientPlayerEntity.data), (float) (Math.toDegrees(previousVehicleYaw - positionAndRotation.yaw) + entityYawOld));
				}
			}

			ridingYawDifference = Math.abs(positionAndRotation.yaw - previousVehicleYaw) > 0.001 ? previousVehicleYaw + Math.toRadians(entityYawOld) : null;
			previousVehicleYaw = positionAndRotation.yaw;
		}
	}

	@Nullable
	public static IntObjectImmutablePair<ObjectObjectImmutablePair<Vector3d, Double>> getRidingVehicleCarNumberAndOffset(long vehicleId) {
		return isRiding(vehicleId) ? new IntObjectImmutablePair<>(ridingVehicleCarNumberCacheOld, new ObjectObjectImmutablePair<>(ridingPositionCacheOld, ridingYawDifference)) : null;
	}

	public static boolean isRiding(long vehicleId) {
		return vehicleId == ridingVehicleId;
	}

	public static void overrideDoors() {
		final double oldDoorOverrideTicks = doorOverrideTicks;
		doorOverrideTicks = 2;
		if (oldDoorOverrideTicks == 0) {
			sendUpdate(false);
		}
	}

	public static boolean hasSeatToggleRequest() {
		return seatToggleRequested;
	}

	public static boolean hasSeatUseRequest() {
		return seatUseRequested;
	}

	public static void consumeSeatUseRequest() {
		seatUseRequested = false;
	}

	public static boolean isSeated() {
		return isSeated;
	}

	public static void toggleSeat(long vehicleId, int carNumber, ObjectCollection<Box> seats, Vector3d playerPosition, ClientPlayerEntity clientPlayerEntity) {
		if (!seatToggleRequested || !isRiding(vehicleId) || ridingVehicleCarNumber != carNumber) {
			return;
		}

		applySeatToggle(seats, playerPosition, clientPlayerEntity);
		seatToggleRequested = false;
	}

	public static void applySeatToggle(ObjectCollection<Box> seats, Vector3d playerPosition, ClientPlayerEntity clientPlayerEntity) {
		if (isSeated) {
			isSeated = false;
			ridingVehicleCarNumberCacheOld = ridingVehicleCarNumber;
			ridingPositionCacheOld = seatedPosition == null ? playerPosition : seatedPosition;
			seatedPosition = null;
			clientPlayerEntity.setPose(EntityPose.STANDING);
			sendUpdate(false);
			return;
		}

		if (seats == null || seats.isEmpty()) {
			seatToggleRequested = false;
			seatUseRequested = false;
			return;
		}

		final Box seat = seats.stream().min(Comparator.comparingDouble(seatBox -> {
			final double centerX = (seatBox.getMinXMapped() + seatBox.getMaxXMapped()) / 2;
			final double centerY = (seatBox.getMinYMapped() + seatBox.getMaxYMapped()) / 2;
			final double centerZ = (seatBox.getMinZMapped() + seatBox.getMaxZMapped()) / 2;
			final double offsetX = centerX - playerPosition.getXMapped();
			final double offsetY = centerY - playerPosition.getYMapped();
			final double offsetZ = centerZ - playerPosition.getZMapped();
			return offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
		})).orElse(null);

		if (seat == null) {
			seatToggleRequested = false;
			seatUseRequested = false;
			return;
		}

		final double seatCenterX = (seat.getMinXMapped() + seat.getMaxXMapped()) / 2;
		final double seatCenterZ = (seat.getMinZMapped() + seat.getMaxZMapped()) / 2;
		final double seatY = seat.getMinYMapped() - 0.35;

		seatedPosition = new Vector3d(seatCenterX, seatY, seatCenterZ);

		isSeated = true;
		isOnGangway = false;
		ridingVehicleX = seatedPosition.getXMapped();
		ridingVehicleY = seatedPosition.getYMapped();
		ridingVehicleZ = seatedPosition.getZMapped();
		ridingVehicleCarNumberCacheOld = ridingVehicleCarNumber;
		ridingPositionCacheOld = seatedPosition;
		ridingPositionCache = seatedPosition;

		clientPlayerEntity.setPose(EntityPose.CROUCHING);
		sendUpdate(false);
	}

	@Nullable
	public static ItemDriverKey getValidHoldingKey(long depotId) {
		final ClientPlayerEntity clientPlayerEntity = MinecraftClient.getInstance().getPlayerMapped();
		if (clientPlayerEntity != null) {
			final ItemStack itemStack1 = clientPlayerEntity.getMainHandStack();
			final Item item1 = itemStack1.getItem();

			if (item1.data instanceof ItemDriverKey) {
				return ItemDepotDriverKey.isCreativeDriverKeyOrMatchesDepot(itemStack1, depotId) ? (ItemDriverKey) item1.data : null;
			}

			final ItemStack itemStack2 = clientPlayerEntity.getOffHandStack();
			final Item item2 = itemStack2.getItem();

			if (item2.data instanceof ItemDriverKey) {
				return ItemDepotDriverKey.isCreativeDriverKeyOrMatchesDepot(itemStack2, depotId) ? (ItemDriverKey) item2.data : null;
			}
		}

		return null;
	}

	public static boolean showShiftProgressBar() {
		final MinecraftClient minecraftClient = MinecraftClient.getInstance();
		final ClientPlayerEntity clientPlayerEntity = minecraftClient.getPlayerMapped();

		if (shiftHoldingTicks > 0 && clientPlayerEntity != null) {
			final int progressFilled = MathHelper.clamp((int) (shiftHoldingTicks * DISMOUNT_PROGRESS_BAR_LENGTH / SHIFT_ACTIVATE_TICKS), 0, DISMOUNT_PROGRESS_BAR_LENGTH);
			final String progressBar = String.format("§6%s§7%s", StringUtils.repeat('|', progressFilled), StringUtils.repeat('|', DISMOUNT_PROGRESS_BAR_LENGTH - progressFilled));

			clientPlayerEntity.sendMessage(TranslationProvider.GUI_MTR_DISMOUNT_HOLD.getText(InitClient.getShiftText(), progressBar), true);
			return false;
		} else {
			return true;
		}
	}

	@Nullable
	private static ObjectBooleanImmutablePair<Box> bestPosition(ObjectArrayList<ObjectBooleanImmutablePair<Box>> floorsOrDoorways, double x, double y, double z) {
		return floorsOrDoorways.stream()
				.filter(floorOrDoorway -> RenderVehicleHelper.boxContains(floorOrDoorway.left(), x, y, z))
				.max(Comparator.comparingDouble(floorOrDoorway -> floorOrDoorway.left().getMaxYMapped()))
				.orElse(floorsOrDoorways.stream().filter(floorOrDoorway -> Math.abs(floorOrDoorway.left().getMaxYMapped() - ridingVehicleY) <= 1).min(Comparator.comparingDouble(floorOrDoorway -> {
					final Box box = floorOrDoorway.left();
					final double minX = box.getMinXMapped();
					final double maxX = box.getMaxXMapped();
					final double minZ = box.getMinZMapped();
					final double maxZ = box.getMaxZMapped();

					return (Utilities.isBetween(x, minX, maxX) ? 0 : Math.min(Math.abs(minX - x), Math.abs(maxX - x))) + (Utilities.isBetween(z, minZ, maxZ) ? 0 : Math.min(Math.abs(minZ - z), Math.abs(maxZ - z)));
				})).orElse(null));
	}

	private static void clampPosition(ObjectArrayList<ObjectBooleanImmutablePair<Box>> floorsAndDoorways, double x, double z, ObjectArrayList<Vector3d> offsets) {
		final ObjectBooleanImmutablePair<Box> floorOrDoorway = bestPosition(floorsAndDoorways, x, ridingVehicleY, z);

		if (floorOrDoorway != null) {
			if (floorOrDoorway.rightBoolean()) {
				offsets.add(new Vector3d(
						Utilities.clamp(x, floorOrDoorway.left().getMinXMapped(), floorOrDoorway.left().getMaxXMapped()) - x,
						floorOrDoorway.left().getMaxYMapped(),
						Utilities.clamp(z, floorOrDoorway.left().getMinZMapped(), floorOrDoorway.left().getMaxZMapped()) - z
				));
			} else if (RenderVehicleHelper.boxContains(floorOrDoorway.left(), x, ridingVehicleY, z)) {
				offsets.add(new Vector3d(0, floorOrDoorway.left().getMaxYMapped(), 0));
			}
		}
	}

	private static void movePlayer(double x, double y, double z) {
		if (InitClient.getGameTick() > 40) {
			final Runnable runnable = () -> {
				final MinecraftClient minecraftClient = MinecraftClient.getInstance();
				final ClientWorld clientWorld = minecraftClient.getWorldMapped();
				final ClientPlayerEntity clientPlayerEntity = minecraftClient.getPlayerMapped();

				if (clientPlayerEntity != null && clientWorld != null) {
					clientPlayerEntity.setFallDistanceMapped(0);
					clientPlayerEntity.setVelocity(0, 0, 0);
					clientPlayerEntity.setMovementSpeed(0);
					clientPlayerEntity.updatePosition(x, y, z);
				}
			};

			runnable.run();
			InitClient.scheduleMovePlayer(runnable);
		}
	}

	private static void resetSeatingState() {
		shiftHoldingTicks = 0;
		isSeated = false;
		seatedPosition = null;
		seatToggleRequested = false;
		seatUseRequested = false;
	}

	private static void sendUpdate(boolean dismount) {
		if (ridingVehicleId != 0) {
			InitClient.REGISTRY_CLIENT.sendPacketToServer(PacketUpdateVehicleRidingEntities.create(
					ridingSidingId,
					ridingVehicleId,
					dismount ? -1 : ridingVehicleCarNumber,
					ridingVehicleX,
					ridingVehicleY,
					ridingVehicleZ,
					isOnGangway,
					isHoldingDriverKey,
					pressingAccelerateTicks == 1,
					pressingBrakeTicks == 1,
					pressingDoorsTicks == 1,
					pressingAtoTicks == 1,
					doorOverrideTicks > 1
			));
			sendPositionUpdateTime = 0;
		}
	}

	private static double getFromScale(double min, double max, double percentage) {
		return (max - min) * percentage + min;
	}
}
