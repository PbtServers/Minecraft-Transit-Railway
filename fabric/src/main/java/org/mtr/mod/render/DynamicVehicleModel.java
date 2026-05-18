package org.mtr.mod.render;

import org.apache.commons.lang3.StringUtils;
import org.mtr.core.data.Data;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.*;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.holder.EntityAbstractMapping;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.EntityModelExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ModelPartExtension;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.Init;
import org.mtr.mod.MutableBox;
import org.mtr.mod.ObjectHolder;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.resource.*;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public final class DynamicVehicleModel extends EntityModelExtension<EntityAbstractMapping> {

	private static final String DEFAULT_OBJ_GROUP = "default";
	private static final double FALLBACK_DEFAULT_OBJ_SCALE = 0.999;
	private static final ObjectOpenHashSet<String> LOGGED_DEBUG_KEYS = new ObjectOpenHashSet<>();
	private static final String[] GLASS_TEXTURE_HINTS = {"fenster", "window", "glass", "tint", "reflexion", "reflex", "alpha", "opacity"};
	private static final String[] DISPLAY_TEXTURE_HINTS = {"matrix", "display", "lcd", "led", "route", "ziel"};
	private static final String[] INTERIOR_TEXTURE_HINTS = {"interior", "lights_interior", "cockpit", "driver", "seat", "seats"};
	private static final String[] DOOR_TEXTURE_HINTS = {"tuer", "door", "fafhrertuer"};
	private static final String[] BODY_TEXTURE_HINTS = {"wagenkasten", "body", "kuzov", "exterior", "karosse", "shell", "n4420td_3d_wagenkasten", "n4416_wagenkasten"};
	private static final String[] LIGHT_TEXTURE_HINTS = {"light", "lights", "lamp", "brake", "indicator", "blink"};
	@Nullable
	private static final Field OBJ_MODEL_RAW_MESHES_FIELD = getField(OptimizedModel.ObjModel.class, "rawMeshes");
	@Nullable
	private static final Constructor<OptimizedModel.ObjModel> OBJ_MODEL_CONSTRUCTOR = getObjModelConstructor();

	public final ModelProperties modelProperties;
	private final Identifier texture;
	@Nullable
	private final String debugObjResource;
	private final boolean debugBbModel;
	private final ObjectArraySet<Box> floors = new ObjectArraySet<>();
	private final ObjectArraySet<Box> doorways = new ObjectArraySet<>();
	private final ObjectArraySet<Box> seats = new ObjectArraySet<>();
	private final Object2ObjectOpenHashMap<PartCondition, Object2ObjectOpenHashMap<RenderStage, OptimizedModelWrapper.MaterialGroupWrapper>> materialGroupsForPartConditionAndRenderStage = new Object2ObjectOpenHashMap<>();
	private final Object2ObjectOpenHashMap<PartCondition, Object2ObjectOpenHashMap<RenderStage, OptimizedModelWrapper.MaterialGroupWrapper>> materialGroupsForPartConditionAndRenderStageDoorsClosed = new Object2ObjectOpenHashMap<>();
	private final Object2ObjectOpenHashMap<PartCondition, Object2ObjectOpenHashMap<RenderStage, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>>> objModelsForPartConditionAndRenderStage = new Object2ObjectOpenHashMap<>();
	private final Object2ObjectOpenHashMap<PartCondition, Object2ObjectOpenHashMap<RenderStage, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>>> objModelsForPartConditionAndRenderStageDoorsClosed = new Object2ObjectOpenHashMap<>();

	public DynamicVehicleModel(BlockbenchModel blockbenchModel, Identifier texture, ModelProperties modelProperties, PositionDefinitions positionDefinitions, String id) {
		super(blockbenchModel.getTextureWidth(), blockbenchModel.getTextureHeight());
		debugObjResource = null;
		debugBbModel = StringUtils.contains(id, "n4420");

		final Object2ObjectOpenHashMap<String, BlockbenchElement> uuidToBlockbenchElement = new Object2ObjectOpenHashMap<>();
		blockbenchModel.getElements().forEach(blockbenchElement -> uuidToBlockbenchElement.put(blockbenchElement.getUuid(), blockbenchElement));

		final Object2ObjectOpenHashMap<String, ObjectObjectImmutablePair<ModelPartExtension, MutableBox>> nameToPart = new Object2ObjectOpenHashMap<>();
		final Object2ObjectOpenHashMap<String, ObjectArrayList<ModelDisplayPart>> nameToDisplayParts = new Object2ObjectOpenHashMap<>();
		blockbenchModel.getOutlines().forEach(blockbenchOutline -> {
			final ObjectHolder<ModelPartExtension> parentModelPart = new ObjectHolder<>(this::createModelPart);
			final MutableBox mutableBox = new MutableBox();
			final ObjectArrayList<ModelDisplayPart> modelDisplayParts = new ObjectArrayList<>();

			iterateChildren(blockbenchOutline, null, id, new GroupTransformations(), (uuid, groupTransformations) -> {
				final BlockbenchElement blockbenchElement = uuidToBlockbenchElement.remove(uuid);
				if (blockbenchElement != null) {
					final ModelDisplayPart modelDisplayPart = new ModelDisplayPart();
					modelDisplayParts.add(modelDisplayPart);
					mutableBox.add(blockbenchElement.setModelPart(parentModelPart.createAndGet().addChild(), groupTransformations, modelDisplayPart, (float) modelProperties.getModelYOffset()));
				}
			});

			if (parentModelPart.exists()) {
				nameToPart.put(blockbenchOutline.getName(), new ObjectObjectImmutablePair<>(parentModelPart.createAndGet(), mutableBox));
			}

			if (!modelDisplayParts.isEmpty()) {
				nameToDisplayParts.put(blockbenchOutline.getName(), modelDisplayParts);
			}
		});

		buildModel();
		modelProperties.addPartsIfEmpty(nameToPart.keySet());
		this.texture = texture;
		this.modelProperties = modelProperties;
		modelProperties.iterateParts(modelPropertiesPart -> modelPropertiesPart.writeCache(texture, nameToPart, nameToDisplayParts, positionDefinitions, floors, doorways, seats, materialGroupsForPartConditionAndRenderStage, materialGroupsForPartConditionAndRenderStageDoorsClosed));
		logBbModelDebug(nameToPart.keySet(), nameToDisplayParts.keySet());
		testDoors(id);
	}

	public DynamicVehicleModel(Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> nameToObjModels, Identifier texture, ModelProperties modelProperties, PositionDefinitions positionDefinitions, String id) {
		this(nameToObjModels, texture, modelProperties, positionDefinitions, id, null);
	}

	public DynamicVehicleModel(Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> nameToObjModels, Identifier texture, ModelProperties modelProperties, PositionDefinitions positionDefinitions, String id, @Nullable String debugObjResource) {
		super(0, 0);
		buildModel();
		modelProperties.addPartsIfEmpty(nameToObjModels.keySet());
		this.texture = texture;
		this.modelProperties = modelProperties;
		this.debugObjResource = debugObjResource;
		debugBbModel = false;
		final int[] matchedGroups = {0};
		modelProperties.iterateParts(modelPropertiesPart -> matchedGroups[0] += modelPropertiesPart.writeCache(nameToObjModels, positionDefinitions, floors, doorways, seats, objModelsForPartConditionAndRenderStage, objModelsForPartConditionAndRenderStageDoorsClosed, modelProperties.getModelYOffset()));
		applyLegacyDefaultObjFallback(nameToObjModels, modelProperties, matchedGroups[0], debugObjResource);
		testDoors(id);
	}

	@Override
	public void setAngles2(EntityAbstractMapping entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int light, int overlay, float red, float green, float blue, float alpha) {
	}

	public void render(StoredMatrixTransformations storedMatrixTransformations, @Nullable VehicleExtension vehicle, int carNumber, int[] scrollingDisplayIndexTracker, int light, ObjectArrayList<ObjectDoubleImmutablePair<Box>> openDoorways, boolean fromResourcePackCreator) {
		if (debugBbModel && LOGGED_DEBUG_KEYS.add("render_bbmodel")) {
			Init.LOGGER.info("[MTR OBJ DEBUG] rendering bbmodel=true");
		}

		final boolean renderDisplaysAfterOptimized = debugBbModel || debugObjResource != null;
		modelProperties.iterateParts(modelPropertiesPart -> modelPropertiesPart.render(texture, storedMatrixTransformations, vehicle, carNumber, scrollingDisplayIndexTracker, light, openDoorways, fromResourcePackCreator, renderDisplaysAfterOptimized));
	}

	public void writeFloorsAndDoorways(
			ObjectArrayList<Box> floors,
			ObjectArrayList<Box> doorways,
			ObjectArrayList<Box> seats,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> materialGroupsForPartCondition,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> materialGroupsForPartConditionDoorsClosed,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> objModelsForPartCondition,
			Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> objModelsForPartConditionDoorsClosed
	) {
		floors.addAll(this.floors);
		doorways.addAll(this.doorways);
		seats.addAll(this.seats);

		materialGroupsForPartConditionAndRenderStage.forEach((partCondition, materialGroupsForRenderStage) -> Data.put(materialGroupsForPartCondition, partCondition, materialGroupsForRenderStage.values(), ObjectArrayList::new));
		materialGroupsForPartConditionAndRenderStageDoorsClosed.forEach((partCondition, materialGroupsForRenderStage) -> Data.put(materialGroupsForPartConditionDoorsClosed, partCondition, materialGroupsForRenderStage.values(), ObjectArrayList::new));
		objModelsForPartConditionAndRenderStage.forEach((partCondition, objModelsForRenderStage) -> Data.put(objModelsForPartCondition, partCondition, flattenCollection(objModelsForRenderStage.values()), ObjectArrayList::new));
		objModelsForPartConditionAndRenderStageDoorsClosed.forEach((partCondition, objModelsForRenderStage) -> Data.put(objModelsForPartConditionDoorsClosed, partCondition, flattenCollection(objModelsForRenderStage.values()), ObjectArrayList::new));
		if (debugObjResource != null && LOGGED_DEBUG_KEYS.add("wrappers_obj")) {
			Init.LOGGER.info("[MTR OBJ DEBUG] vehicle n4420 model entry obj wrappers={}", countObjWrappers(objModelsForPartCondition) + countObjWrappers(objModelsForPartConditionDoorsClosed));
		}
		if (debugBbModel && LOGGED_DEBUG_KEYS.add("wrappers_bbmodel")) {
			Init.LOGGER.info("[MTR OBJ DEBUG] vehicle n4420 model entry bbmodel wrappers={}", countMaterialWrappers(materialGroupsForPartCondition) + countMaterialWrappers(materialGroupsForPartConditionDoorsClosed));
		}

		materialGroupsForPartConditionAndRenderStage.clear();
		materialGroupsForPartConditionAndRenderStageDoorsClosed.clear();
		objModelsForPartConditionAndRenderStage.clear();
		objModelsForPartConditionAndRenderStageDoorsClosed.clear();
	}

	/**
	 * Simulate door movement to see if doors overlap (meaning that door X and Z multipliers were set incorrectly)
	 */
	private void testDoors(String id) {
		final long startTime = System.nanoTime();
		final ObjectArrayList<ObjectArrayList<Box>> boxesList = new ObjectArrayList<>();
		final int slices = 5;
		for (int i = 0; i <= slices; i++) {
			final ObjectArrayList<Box> boxes = new ObjectArrayList<>();
			final double time = (double) i / slices;
			modelProperties.iterateParts(modelPropertiesPart -> modelPropertiesPart.getOpenDoorBounds(boxes, time));
			boxesList.add(boxes);
		}

		final int count = boxesList.get(0).size();
		for (int i = 0; i < count; i++) {
			for (int j = i + 1; j < count; j++) {
				if (!boxesList.get(0).get(i).intersects(boxesList.get(0).get(j))) {
					for (int k = 1; k <= slices; k++) {
						if (boxesList.get(k).get(i).intersects(boxesList.get(k).get(j))) {
							Init.LOGGER.warn("Vehicle doors overlapping! Door X and Z multipliers were probably set incorrectly ({})", id);
							return;
						}
					}
				}
			}
		}

		CustomResourceLoader.incrementTestDuration(System.nanoTime() - startTime);
	}

	private static void iterateChildren(BlockbenchOutline blockbenchOutline, @Nullable BlockbenchOutline previousBlockbenchOutline, String id, GroupTransformations groupTransformations, BiConsumer<String, GroupTransformations> consumer) {
		final GroupTransformations newGroupTransformations = blockbenchOutline.add(groupTransformations, previousBlockbenchOutline, id);
		blockbenchOutline.childrenUuid.forEach(uuid -> consumer.accept(uuid, newGroupTransformations));
		blockbenchOutline.getChildren().forEach(childOutline -> iterateChildren(childOutline, blockbenchOutline, id, groupTransformations, consumer));
	}

	private static <T> ObjectArrayList<T> flattenCollection(ObjectCollection<? extends ObjectCollection<T>> collection) {
		final ObjectArrayList<T> combinedList = new ObjectArrayList<>();
		collection.forEach(combinedList::addAll);
		return combinedList;
	}

	private void applyLegacyDefaultObjFallback(Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> nameToObjModels, ModelProperties modelProperties, int matchedGroups, @Nullable String debugObjResource) {
		if (matchedGroups > 0) {
			if (debugObjResource != null) {
				Init.LOGGER.info("[MTR OBJ DEBUG] fallback default group not needed; matchedGroups={}", matchedGroups);
			}
			return;
		}

		if (nameToObjModels.size() != 1) {
			logFallbackSkip(debugObjResource, "obj group count is " + nameToObjModels.size());
			return;
		}

		final OptimizedModel.ObjModel defaultObjModel = nameToObjModels.get(DEFAULT_OBJ_GROUP);
		if (defaultObjModel == null) {
			logFallbackSkip(debugObjResource, "default group missing");
			return;
		}

		final ObjectArrayList<FallbackObjBucket> fallbackObjBuckets = createLegacyFallbackObjBuckets(defaultObjModel, modelProperties, debugObjResource);
		if (fallbackObjBuckets.isEmpty()) {
			logFallbackSkip(debugObjResource, "no fallback meshes remain after classification");
			return;
		}
		fallbackObjBuckets.forEach(fallbackObjBucket -> {
			fallbackObjBucket.objModel.applyScale(FALLBACK_DEFAULT_OBJ_SCALE, FALLBACK_DEFAULT_OBJ_SCALE, FALLBACK_DEFAULT_OBJ_SCALE);
			final OptimizedModelWrapper.ObjModelWrapper defaultObjModelWrapper = new OptimizedModelWrapper.ObjModelWrapper(fallbackObjBucket.objModel);
			defaultObjModelWrapper.addTransformation(fallbackObjBucket.shaderType, 0, -modelProperties.getModelYOffset(), 0, false);
			Data.put(objModelsForPartConditionAndRenderStage, PartCondition.NORMAL, fallbackObjBucket.renderStage, oldValue -> addFallbackObjModel(oldValue, defaultObjModelWrapper), Object2ObjectOpenHashMap::new);
			Data.put(objModelsForPartConditionAndRenderStageDoorsClosed, PartCondition.NORMAL, fallbackObjBucket.renderStage, oldValue -> addFallbackObjModel(oldValue, defaultObjModelWrapper), Object2ObjectOpenHashMap::new);
		});

		if (debugObjResource != null) {
			Init.LOGGER.info("[MTR OBJ DEBUG] fallback default group enabled for {}", debugObjResource);
			Init.LOGGER.info("[MTR OBJ DEBUG] fallback wrapper optimizedModel=true");
			Init.LOGGER.info("[MTR OBJ DEBUG] fallback render invoked normalPath=true earlyQueue=false");
			Init.LOGGER.info("[MTR OBJ DEBUG] fallback visible candidate=true");
		}
	}

	private static ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> addFallbackObjModel(@Nullable ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> oldValue, OptimizedModelWrapper.ObjModelWrapper objModelWrapper) {
		final ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper> newObjModels = oldValue == null ? new ObjectArrayList<>() : oldValue;
		newObjModels.add(objModelWrapper);
		return newObjModels;
	}

	private static void logFallbackSkip(@Nullable String debugObjResource, String reason) {
		if (debugObjResource != null) {
			Init.LOGGER.info("[MTR OBJ DEBUG] fallback default group skipped reason={}", reason);
		}
	}

	private static int countObjWrappers(Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> objModels) {
		return objModels.values().stream().mapToInt(ObjectArrayList::size).sum();
	}

	private static int countMaterialWrappers(Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> materialGroups) {
		return materialGroups.values().stream().mapToInt(ObjectArrayList::size).sum();
	}

	private ObjectArrayList<FallbackObjBucket> createLegacyFallbackObjBuckets(OptimizedModel.ObjModel defaultObjModel, ModelProperties modelProperties, @Nullable String debugObjResource) {
		final List<?> rawMeshes = getRawMeshes(defaultObjModel);
		if (rawMeshes == null || rawMeshes.isEmpty()) {
			logFallbackSkip(debugObjResource, "raw mesh list unavailable");
			return new ObjectArrayList<>();
		}

		final Object2ObjectOpenHashMap<FallbackCategory, ObjectArrayList<Object>> meshesByCategory = new Object2ObjectOpenHashMap<>();
		final Object2ObjectOpenHashMap<String, ObjectObjectImmutablePair<FallbackCategory, Integer>> textureAssignments = new Object2ObjectOpenHashMap<>();
		final Object2ObjectOpenHashMap<String, ObjectIntImmutablePair<String>> excludedTextureInfo = new Object2ObjectOpenHashMap<>();
		final boolean hasDisplayParts = modelProperties.hasDisplayParts();
		final boolean hasDoorParts = modelProperties.hasDoorParts();
		final ObjectLinkedOpenHashSet<String> objDisplayTextures = new ObjectLinkedOpenHashSet<>();
		final ObjectLinkedOpenHashSet<String> objDoorTextures = new ObjectLinkedOpenHashSet<>();

		rawMeshes.forEach(rawMesh -> {
			final String textureName = getMeshTextureName(rawMesh);
			final FallbackCategory fallbackCategory = classifyFallbackCategory(textureName, hasDisplayParts, hasDoorParts);
			if (fallbackCategory == FallbackCategory.EXCLUDED_DISPLAY || fallbackCategory == FallbackCategory.TEXT_LIGHT) {
				objDisplayTextures.add(textureName);
			}
			if (fallbackCategory == FallbackCategory.EXCLUDED_DOOR || fallbackCategory == FallbackCategory.DOOR_STATIC) {
				objDoorTextures.add(textureName);
			}
			if (fallbackCategory.excluded) {
				addExcludedTexture(excludedTextureInfo, textureName, fallbackCategory.reason);
			} else {
				meshesByCategory.computeIfAbsent(fallbackCategory, key -> new ObjectArrayList<>()).add(rawMesh);
				addTextureAssignment(textureAssignments, textureName, fallbackCategory);
			}
		});

		if (debugObjResource != null && LOGGED_DEBUG_KEYS.add("fallback_meshes_" + debugObjResource)) {
			Init.LOGGER.info("[MTR OBJ DEBUG] fallback raw meshes total={} selected={}", rawMeshes.size(), meshesByCategory.values().stream().mapToInt(ObjectArrayList::size).sum());
			textureAssignments.object2ObjectEntrySet().stream()
					.sorted((entry1, entry2) -> Integer.compare(entry2.getValue().right(), entry1.getValue().right()))
					.forEach(entry -> Init.LOGGER.info("[MTR OBJ DEBUG] mesh texture={} count={} assignedLayer={} reason={}", entry.getKey(), entry.getValue().right(), entry.getValue().left().layerName, entry.getValue().left().reason));
			excludedTextureInfo.object2ObjectEntrySet().stream()
					.sorted((entry1, entry2) -> Integer.compare(entry2.getValue().rightInt(), entry1.getValue().rightInt()))
					.forEach(entry -> Init.LOGGER.info("[MTR OBJ DEBUG] excluded texture={} reason={} count={}", entry.getKey(), entry.getValue().left(), entry.getValue().rightInt()));
			Init.LOGGER.info("[MTR OBJ DEBUG] bbmodel display parts={}", hasDisplayParts);
			Init.LOGGER.info("[MTR OBJ DEBUG] obj display textures={}", objDisplayTextures);
			Init.LOGGER.info("[MTR OBJ DEBUG] display handling={}", hasDisplayParts ? "excluded_for_bbmodel" : "rendered_as_text");
			Init.LOGGER.info("[MTR OBJ DEBUG] modelProperties door candidates={}", modelProperties.getDoorPartNames());
			Init.LOGGER.info("[MTR OBJ DEBUG] obj door textures={}", objDoorTextures);
			Init.LOGGER.info("[MTR OBJ DEBUG] door handling={}", hasDoorParts ? "excluded_for_dynamic" : "static_fallback");
		}

		final ObjectArrayList<FallbackObjBucket> fallbackObjBuckets = new ObjectArrayList<>();
		for (final FallbackCategory fallbackCategory : FallbackCategory.values()) {
			if (fallbackCategory.excluded) {
				continue;
			}
			final ObjectArrayList<Object> categoryMeshes = meshesByCategory.get(fallbackCategory);
			if (categoryMeshes == null || categoryMeshes.isEmpty()) {
				continue;
			}
			final OptimizedModel.ObjModel filteredObjModel = instantiateFilteredObjModel(categoryMeshes);
			if (filteredObjModel != null) {
				fallbackObjBuckets.add(new FallbackObjBucket(filteredObjModel, fallbackCategory.shaderType, fallbackCategory.renderStage));
			} else {
				logFallbackSkip(debugObjResource, "failed to instantiate filtered obj model for " + fallbackCategory.layerName);
			}
		}

		if (debugObjResource != null) {
			final Object2IntOpenHashMap<FallbackCategory> categoryCounts = new Object2IntOpenHashMap<>();
			meshesByCategory.forEach((category, meshes) -> categoryCounts.put(category, meshes.size()));
			Init.LOGGER.info("[MTR OBJ DEBUG] legacy fallback category exterior_body={}", categoryCounts.getInt(FallbackCategory.BODY));
			Init.LOGGER.info("[MTR OBJ DEBUG] legacy fallback category exterior_detail={}", categoryCounts.getInt(FallbackCategory.DETAILS));
			Init.LOGGER.info("[MTR OBJ DEBUG] legacy fallback category glass_translucent={}", categoryCounts.getInt(FallbackCategory.GLASS));
			Init.LOGGER.info("[MTR OBJ DEBUG] legacy fallback category interior={}", categoryCounts.getInt(FallbackCategory.INTERIOR));
			Init.LOGGER.info("[MTR OBJ DEBUG] legacy fallback category lights={}", categoryCounts.getInt(FallbackCategory.LIGHTS));
			Init.LOGGER.info("[MTR OBJ DEBUG] legacy fallback category display_excluded={}", excludedTextureInfo.values().stream().filter(pair -> StringUtils.equals(pair.left(), FallbackCategory.EXCLUDED_DISPLAY.reason)).mapToInt(ObjectIntImmutablePair::rightInt).sum());
			Init.LOGGER.info("[MTR OBJ DEBUG] legacy fallback category door_static={}", categoryCounts.getInt(FallbackCategory.DOOR_STATIC));
			Init.LOGGER.info("[MTR OBJ DEBUG] legacy fallback category door_excluded={}", excludedTextureInfo.values().stream().filter(pair -> StringUtils.equals(pair.left(), FallbackCategory.EXCLUDED_DOOR.reason)).mapToInt(ObjectIntImmutablePair::rightInt).sum());
			Init.LOGGER.info("[MTR OBJ DEBUG] fallback using classified meshes for {}", debugObjResource);
		}
		return fallbackObjBuckets;
	}

	private void logBbModelDebug(Collection<String> partNames, Collection<String> displayPartNames) {
		if (!debugBbModel || !LOGGED_DEBUG_KEYS.add("bbmodel_parts")) {
			return;
		}
		final ObjectLinkedOpenHashSet<String> candidateNames = new ObjectLinkedOpenHashSet<>();
		partNames.forEach(name -> {
			if (containsDebugCandidate(name)) {
				candidateNames.add(name);
			}
		});
		displayPartNames.forEach(name -> {
			if (containsDebugCandidate(name)) {
				candidateNames.add(name);
			}
		});
		final ObjectLinkedOpenHashSet<String> doorCandidateNames = new ObjectLinkedOpenHashSet<>();
		partNames.forEach(name -> {
			final String lowercaseName = name.toLowerCase(Locale.ROOT);
			if (lowercaseName.contains("door") || lowercaseName.contains("tuer")) {
				doorCandidateNames.add(name);
			}
		});
		Init.LOGGER.info("[MTR OBJ DEBUG] bbmodel parts={}", partNames.size());
		Init.LOGGER.info("[MTR OBJ DEBUG] bbmodel has display parts={}", !displayPartNames.isEmpty());
		Init.LOGGER.info("[MTR OBJ DEBUG] bbmodel has door/window/interior candidates={}", candidateNames);
		Init.LOGGER.info("[MTR OBJ DEBUG] bbmodel door candidates={}", doorCandidateNames);
	}

	private static boolean containsDebugCandidate(String name) {
		final String lowercaseName = name.toLowerCase(Locale.ROOT);
		return lowercaseName.contains("door") || lowercaseName.contains("tuer") || lowercaseName.contains("window") || lowercaseName.contains("glass") || lowercaseName.contains("fenster") || lowercaseName.contains("interior") || lowercaseName.contains("salon") || lowercaseName.contains("display") || lowercaseName.contains("route");
	}

	@Nullable
	private static List<?> getRawMeshes(OptimizedModel.ObjModel objModel) {
		if (OBJ_MODEL_RAW_MESHES_FIELD == null) {
			return null;
		}
		try {
			return (List<?>) OBJ_MODEL_RAW_MESHES_FIELD.get(objModel);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	private static String getMeshTextureName(Object rawMesh) {
		try {
			final Object materialProperties = rawMesh.getClass().getField("materialProperties").get(rawMesh);
			return ((org.mtr.mapping.render.batch.MaterialProperties) materialProperties).getTexture().data.toString();
		} catch (Exception e) {
			return "<unknown>";
		}
	}

	private static FallbackCategory classifyFallbackCategory(String textureName, boolean hasDisplayParts, boolean hasDoorParts) {
		final String lowercaseTextureName = textureName.toLowerCase(Locale.ROOT);
		if (containsAnyHint(lowercaseTextureName, GLASS_TEXTURE_HINTS)) {
			return FallbackCategory.GLASS;
		}
		if (containsAnyHint(lowercaseTextureName, DISPLAY_TEXTURE_HINTS)) {
			return hasDisplayParts ? FallbackCategory.EXCLUDED_DISPLAY : FallbackCategory.TEXT_LIGHT;
		}
		if (containsAnyHint(lowercaseTextureName, INTERIOR_TEXTURE_HINTS)) {
			return FallbackCategory.INTERIOR;
		}
		if (containsAnyHint(lowercaseTextureName, DOOR_TEXTURE_HINTS)) {
			return hasDoorParts ? FallbackCategory.EXCLUDED_DOOR : FallbackCategory.DOOR_STATIC;
		}
		if (containsAnyHint(lowercaseTextureName, LIGHT_TEXTURE_HINTS)) {
			return FallbackCategory.LIGHTS;
		}
		if (containsAnyHint(lowercaseTextureName, BODY_TEXTURE_HINTS)) {
			return FallbackCategory.BODY;
		}
		return FallbackCategory.DETAILS;
	}

	private static boolean containsAnyHint(String lowercaseTextureName, String[] hints) {
		for (final String hint : hints) {
			if (lowercaseTextureName.contains(hint)) {
				return true;
			}
		}
		return false;
	}

	private static void addExcludedTexture(Object2ObjectOpenHashMap<String, ObjectIntImmutablePair<String>> excludedTextureInfo, String textureName, String reason) {
		final ObjectIntImmutablePair<String> existingPair = excludedTextureInfo.get(textureName);
		excludedTextureInfo.put(textureName, new ObjectIntImmutablePair<>(reason, existingPair == null ? 1 : existingPair.rightInt() + 1));
	}

	private static void addTextureAssignment(Object2ObjectOpenHashMap<String, ObjectObjectImmutablePair<FallbackCategory, Integer>> textureAssignments, String textureName, FallbackCategory fallbackCategory) {
		final ObjectObjectImmutablePair<FallbackCategory, Integer> existingPair = textureAssignments.get(textureName);
		textureAssignments.put(textureName, new ObjectObjectImmutablePair<>(fallbackCategory, existingPair == null ? 1 : existingPair.right() + 1));
	}

	@Nullable
	private static OptimizedModel.ObjModel instantiateFilteredObjModel(ObjectArrayList<Object> selectedRawMeshes) {
		if (OBJ_MODEL_CONSTRUCTOR == null) {
			return null;
		}

		final float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		selectedRawMeshes.forEach(rawMesh -> {
			try {
				final List<?> vertices = (List<?>) rawMesh.getClass().getField("vertices").get(rawMesh);
				vertices.forEach(vertex -> {
					try {
						final Object position = vertex.getClass().getField("position").get(vertex);
						final float x = (float) position.getClass().getMethod("getX").invoke(position);
						final float y = (float) position.getClass().getMethod("getY").invoke(position);
						final float z = (float) position.getClass().getMethod("getZ").invoke(position);
						bounds[0] = Math.min(bounds[0], x);
						bounds[1] = Math.min(bounds[1], y);
						bounds[2] = Math.min(bounds[2], z);
						bounds[3] = Math.max(bounds[3], x);
						bounds[4] = Math.max(bounds[4], y);
						bounds[5] = Math.max(bounds[5], z);
					} catch (Exception ignored) {
					}
				});
			} catch (Exception ignored) {
			}
		});

		try {
			return OBJ_MODEL_CONSTRUCTOR.newInstance(selectedRawMeshes, false, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
		} catch (Exception e) {
			return null;
		}
	}

	@Nullable
	private static Field getField(Class<?> clazz, String name) {
		try {
			final Field field = clazz.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (Exception e) {
			return null;
		}
	}

	@Nullable
	private static Constructor<OptimizedModel.ObjModel> getObjModelConstructor() {
		try {
			final Constructor<OptimizedModel.ObjModel> constructor = OptimizedModel.ObjModel.class.getDeclaredConstructor(List.class, boolean.class, float.class, float.class, float.class, float.class, float.class, float.class);
			constructor.setAccessible(true);
			return constructor;
		} catch (Exception e) {
			return null;
		}
	}

	private enum FallbackCategory {
		BODY("EXTERIOR", "body", OptimizedModel.ShaderType.CUTOUT, RenderStage.EXTERIOR, false),
		GLASS("EXTERIOR_TRANSLUCENT", "glass", OptimizedModel.ShaderType.TRANSLUCENT, RenderStage.EXTERIOR, false),
		INTERIOR("INTERIOR", "interior", OptimizedModel.ShaderType.CUTOUT_BRIGHT, RenderStage.INTERIOR, false),
		TEXT_LIGHT("LIGHT_2", "text-light", OptimizedModel.ShaderType.CUTOUT_GLOWING, RenderStage.LIGHT, false),
		LIGHTS("LIGHT_2", "lights", OptimizedModel.ShaderType.CUTOUT_GLOWING, RenderStage.LIGHT, false),
		DOOR_STATIC("EXTERIOR", "door-static", OptimizedModel.ShaderType.CUTOUT, RenderStage.EXTERIOR, false),
		DETAILS("EXTERIOR", "details", OptimizedModel.ShaderType.CUTOUT, RenderStage.EXTERIOR, false),
		EXCLUDED_DISPLAY("SKIP", "bbmodel-display", OptimizedModel.ShaderType.CUTOUT, RenderStage.EXTERIOR, true),
		EXCLUDED_DOOR("SKIP", "dynamic-door", OptimizedModel.ShaderType.CUTOUT, RenderStage.EXTERIOR, true);

		private final String layerName;
		private final String reason;
		private final OptimizedModel.ShaderType shaderType;
		private final RenderStage renderStage;
		private final boolean excluded;

		FallbackCategory(String layerName, String reason, OptimizedModel.ShaderType shaderType, RenderStage renderStage, boolean excluded) {
			this.layerName = layerName;
			this.reason = reason;
			this.shaderType = shaderType;
			this.renderStage = renderStage;
			this.excluded = excluded;
		}
	}

	private static final class FallbackObjBucket {
		private final OptimizedModel.ObjModel objModel;
		private final OptimizedModel.ShaderType shaderType;
		private final RenderStage renderStage;

		private FallbackObjBucket(OptimizedModel.ObjModel objModel, OptimizedModel.ShaderType shaderType, RenderStage renderStage) {
			this.objModel = objModel;
			this.shaderType = shaderType;
			this.renderStage = renderStage;
		}
	}
}
