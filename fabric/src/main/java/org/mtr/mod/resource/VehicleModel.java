package org.mtr.mod.resource;

import org.apache.commons.lang3.StringUtils;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mod.Init;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.generated.resource.VehicleModelSchema;
import org.mtr.mod.render.DynamicVehicleModel;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

public final class VehicleModel extends VehicleModelSchema {

	private static final String DEBUG_OBJ_PATH = "n4420/n4420.obj";
	private static final ObjectLinkedOpenHashSet<String> LOGGED_DEBUG_KEYS = new ObjectLinkedOpenHashSet<>();
	private static final String DEFAULT_OBJ_GROUP = "default";
	@Nullable
	private static final Field OBJ_MODEL_RAW_MESHES_FIELD = getField(OptimizedModel.ObjModel.class, "rawMeshes");
	@Nullable
	private static final Constructor<OptimizedModel.ObjModel> OBJ_MODEL_CONSTRUCTOR = getObjModelConstructor();

	boolean shouldPreload = false;
	final CachedResource<DynamicVehicleModel> cachedModel;
	private final JsonReader modelPropertiesJsonReader;
	private final JsonReader positionDefinitionsJsonReader;

	public static final int MODEL_LIFESPAN = 60000;

	public VehicleModel(ReaderBase readerBase, ResourceProvider resourceProvider) {
		super(readerBase, resourceProvider);
		updateData(readerBase);
		modelPropertiesJsonReader = new JsonReader(Utilities.parseJson(resourceProvider.get(CustomResourceTools.formatIdentifierWithDefault(modelPropertiesResource, "json"))));
		positionDefinitionsJsonReader = new JsonReader(Utilities.parseJson(resourceProvider.get(CustomResourceTools.formatIdentifierWithDefault(positionDefinitionsResource, "json"))));
		cachedModel = new CachedResource<>(() -> createModel(new ModelProperties(modelPropertiesJsonReader), new PositionDefinitions(positionDefinitionsJsonReader), modelPropertiesResource), shouldPreload ? Integer.MAX_VALUE : MODEL_LIFESPAN);
	}

	public VehicleModel(ReaderBase readerBase, JsonReader modelPropertiesJsonReader, JsonReader positionDefinitionsJsonReader, String id, ResourceProvider resourceProvider) {
		super(readerBase, resourceProvider);
		updateData(readerBase);
		this.modelPropertiesJsonReader = modelPropertiesJsonReader;
		this.positionDefinitionsJsonReader = positionDefinitionsJsonReader;
		cachedModel = new CachedResource<>(() -> createModel(new ModelProperties(modelPropertiesJsonReader), new PositionDefinitions(positionDefinitionsJsonReader), id), shouldPreload ? Integer.MAX_VALUE : MODEL_LIFESPAN);
	}

	VehicleModel(
			String modelResource,
			String textureResource,
			String modelPropertiesResource,
			String positionDefinitionsResource,
			boolean flipTextureV,
			ResourceProvider resourceProvider
	) {
		super(
				modelResource,
				textureResource,
				modelPropertiesResource,
				positionDefinitionsResource,
				flipTextureV,
				resourceProvider
		);
		modelPropertiesJsonReader = new JsonReader(Utilities.parseJson(resourceProvider.get(CustomResourceTools.formatIdentifierWithDefault(modelPropertiesResource, "json"))));
		positionDefinitionsJsonReader = new JsonReader(Utilities.parseJson(resourceProvider.get(CustomResourceTools.formatIdentifierWithDefault(positionDefinitionsResource, "json"))));
		cachedModel = new CachedResource<>(() -> createModel(new ModelProperties(modelPropertiesJsonReader), new PositionDefinitions(positionDefinitionsJsonReader), modelPropertiesResource), shouldPreload ? Integer.MAX_VALUE : MODEL_LIFESPAN);
	}

	public MinecraftModelResource getAsMinecraftResource() {
		return new MinecraftModelResource(modelResource, modelPropertiesResource, positionDefinitionsResource);
	}

	public void addToTextureResource(ObjectArraySet<String> textureResources) {
		final ModelProperties modelProperties = new ModelProperties(modelPropertiesJsonReader);
		if (modelProperties.gangwayInnerSideTexture != null) {
			textureResources.add(modelProperties.gangwayInnerSideTexture.data.toString());
		}
		if (modelProperties.gangwayInnerTopTexture != null) {
			textureResources.add(modelProperties.gangwayInnerTopTexture.data.toString());
		}
		if (modelProperties.gangwayInnerBottomTexture != null) {
			textureResources.add(modelProperties.gangwayInnerBottomTexture.data.toString());
		}
		if (modelProperties.gangwayOuterSideTexture != null) {
			textureResources.add(modelProperties.gangwayOuterSideTexture.data.toString());
		}
		if (modelProperties.gangwayOuterTopTexture != null) {
			textureResources.add(modelProperties.gangwayOuterTopTexture.data.toString());
		}
		if (modelProperties.gangwayOuterBottomTexture != null) {
			textureResources.add(modelProperties.gangwayOuterBottomTexture.data.toString());
		}
		if (modelProperties.barrierInnerSideTexture != null) {
			textureResources.add(modelProperties.barrierInnerSideTexture.data.toString());
		}
		if (modelProperties.barrierInnerTopTexture != null) {
			textureResources.add(modelProperties.barrierInnerTopTexture.data.toString());
		}
		if (modelProperties.barrierInnerBottomTexture != null) {
			textureResources.add(modelProperties.barrierInnerBottomTexture.data.toString());
		}
		if (modelProperties.barrierOuterSideTexture != null) {
			textureResources.add(modelProperties.barrierOuterSideTexture.data.toString());
		}
		if (modelProperties.barrierOuterTopTexture != null) {
			textureResources.add(modelProperties.barrierOuterTopTexture.data.toString());
		}
		if (modelProperties.barrierOuterBottomTexture != null) {
			textureResources.add(modelProperties.barrierOuterBottomTexture.data.toString());
		}
		textureResources.add(textureResource);
	}

	VehicleModelWrapper toVehicleModelWrapper() {
		final ModelProperties modelProperties = new ModelProperties(modelPropertiesJsonReader);
		final PositionDefinitions positionDefinitions = new PositionDefinitions(positionDefinitionsJsonReader);
		final ObjectArrayList<ModelPropertiesPartWrapper> parts = new ObjectArrayList<>();
		modelProperties.iterateParts(modelPropertiesPart -> modelPropertiesPart.addToModelPropertiesPartWrapperMap(positionDefinitions, parts));
		return modelProperties.toVehicleModelWrapper(modelResource, textureResource, modelPropertiesResource, positionDefinitionsResource, flipTextureV, parts);
	}

	boolean isDebugN4420() {
		return StringUtils.contains(CustomResourceTools.formatIdentifierWithDefault(modelResource, "obj").data.toString(), DEBUG_OBJ_PATH);
	}

	boolean isObjModelResource() {
		return modelResource.endsWith(".obj");
	}

	boolean isBbModelResource() {
		return modelResource.endsWith(".bbmodel");
	}

	private DynamicVehicleModel createModel(ModelProperties modelProperties, PositionDefinitions positionDefinitions, String id) {
		final Identifier textureId = CustomResourceTools.formatIdentifierWithDefault(textureResource, "png");

		if (modelResource.endsWith(".bbmodel")) {
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.beginReload();
			final DynamicVehicleModel dynamicVehicleModel = new DynamicVehicleModel(
					new BlockbenchModel(new JsonReader(Utilities.parseJson(resourceProvider.get(CustomResourceTools.formatIdentifierWithDefault(modelResource, "bbmodel"))))),
					textureId,
					modelProperties,
					positionDefinitions,
					id
			);
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.finishReload();
			return dynamicVehicleModel;
		} else if (modelResource.endsWith(".obj")) {
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.beginReload();
			final Identifier objIdentifier = CustomResourceTools.formatIdentifierWithDefault(modelResource, "obj");
			final String objContents = resourceProvider.get(objIdentifier);
			logRawObjAndModelProperties(objIdentifier.data.toString(), objContents, modelProperties);
			final Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> nameToObjModels = rebuildObjModelsByObjectName(
					objIdentifier.data.toString(),
					objContents,
					modelProperties,
					new Object2ObjectAVLTreeMap<>(OptimizedModel.ObjModel.loadModel(
							objContents,
							mtlString -> getMtlContents(objIdentifier.data.toString(), mtlString),
							textureString -> StringUtils.isEmpty(textureString) ? OptimizedModelWrapper.WHITE_TEXTURE : StringUtils.equals(textureString, "default.png") ? textureId : CustomResourceTools.getResourceFromSamePath(modelResource, textureString, "png"),
							null, true, flipTextureV
					))
			);
			final DynamicVehicleModel dynamicVehicleModel = new DynamicVehicleModel(nameToObjModels, textureId, modelProperties, positionDefinitions, id, StringUtils.contains(objIdentifier.data.toString(), DEBUG_OBJ_PATH) ? objIdentifier.data.toString() : null);
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.finishReload();
			return dynamicVehicleModel;
		} else {
			Init.LOGGER.error("[{}] Invalid model!", textureId.data.toString());
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.beginReload();
			final DynamicVehicleModel dynamicVehicleModel = new DynamicVehicleModel(
					new BlockbenchModel(new JsonReader(new JsonObject())),
					textureId,
					modelProperties,
					positionDefinitions,
					id
			);
			CustomResourceLoader.OPTIMIZED_RENDERER_WRAPPER.finishReload();
			return dynamicVehicleModel;
		}
	}

	private Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> rebuildObjModelsByObjectName(String objIdentifier, String objContents, ModelProperties modelProperties, Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> loadedObjModels) {
		if (!shouldRebuildObjGroups(objContents, modelProperties, loadedObjModels)) {
			return loadedObjModels;
		}

		final OptimizedModel.ObjModel defaultObjModel = loadedObjModels.get(DEFAULT_OBJ_GROUP);
		final List<?> rawMeshes = defaultObjModel == null ? null : getRawMeshes(defaultObjModel);
		if (rawMeshes == null || rawMeshes.isEmpty()) {
			logReconstructionResult(objIdentifier, 0, 0, false, new Object2ObjectAVLTreeMap<>(), new Object2ObjectOpenHashMap<>(), modelProperties, true);
			return loadedObjModels;
		}

		final Object2ObjectOpenHashMap<String, String> materialToTexture = parseMaterialToTextureMap(objIdentifier, objContents);
		final ObjectArrayList<ObjectMaterialPair> objectMaterialPairs = parseObjectMaterialPairs(objContents, materialToTexture);
		if (StringUtils.contains(objIdentifier, DEBUG_OBJ_PATH)) {
			Init.LOGGER.info("[MTR OBJ DEBUG] object/material pairs={}", objectMaterialPairs.size());
			Init.LOGGER.info("[MTR OBJ DEBUG] rawMeshes={}", rawMeshes.size());
		}
		if (objectMaterialPairs.isEmpty()) {
			logReconstructionResult(objIdentifier, 0, rawMeshes.size(), false, new Object2ObjectAVLTreeMap<>(), new Object2ObjectOpenHashMap<>(), modelProperties, true);
			return loadedObjModels;
		}

		final Object2ObjectOpenHashMap<String, ObjectArrayList<Object>> groupedRawMeshes = new Object2ObjectOpenHashMap<>();
		int pairIndex = 0;
		int matchedMeshCount = 0;

		for (final Object rawMesh : rawMeshes) {
			final String meshTextureName = normalizeTextureName(getMeshTextureName(rawMesh));
			final int matchedPairIndex = findMatchingPairIndex(objectMaterialPairs, pairIndex, meshTextureName);
			if (matchedPairIndex < 0) {
				continue;
			}

			pairIndex = matchedPairIndex + 1;
			final ObjectMaterialPair objectMaterialPair = objectMaterialPairs.get(matchedPairIndex);
			groupedRawMeshes.computeIfAbsent(objectMaterialPair.objectName, key -> new ObjectArrayList<>()).add(rawMesh);
			matchedMeshCount++;
		}

		final Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> rebuiltObjModels = new Object2ObjectAVLTreeMap<>();
		final Object2ObjectOpenHashMap<String, ObjectLinkedOpenHashSet<String>> reconstructedTexturesByObject = new Object2ObjectOpenHashMap<>();
		groupedRawMeshes.forEach((objectName, groupedMeshes) -> {
			final OptimizedModel.ObjModel rebuiltObjModel = instantiateFilteredObjModel(groupedMeshes);
			if (rebuiltObjModel != null) {
				rebuiltObjModels.put(objectName, rebuiltObjModel);
				final ObjectLinkedOpenHashSet<String> textureNames = new ObjectLinkedOpenHashSet<>();
				groupedMeshes.forEach(groupedMesh -> textureNames.add(getMeshTextureName(groupedMesh)));
				reconstructedTexturesByObject.put(objectName, textureNames);
				if (StringUtils.contains(objIdentifier, DEBUG_OBJ_PATH)) {
					Init.LOGGER.info("[MTR OBJ DEBUG] reconstructed object={} meshes={}", objectName, groupedMeshes.size());
				}
			}
		});

		final boolean reconstructionMatched = matchedMeshCount > 0 && !rebuiltObjModels.isEmpty();
		logReconstructionResult(objIdentifier, matchedMeshCount, rawMeshes.size(), reconstructionMatched, rebuiltObjModels, reconstructedTexturesByObject, modelProperties, false);
		return reconstructionMatched ? rebuiltObjModels : loadedObjModels;
	}

	private boolean shouldRebuildObjGroups(String objContents, ModelProperties modelProperties, Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> loadedObjModels) {
		if (loadedObjModels.size() != 1 || !loadedObjModels.containsKey(DEFAULT_OBJ_GROUP)) {
			return false;
		}

		final ObjectLinkedOpenHashSet<String> objectNames = new ObjectLinkedOpenHashSet<>();
		for (final String line : objContents.split("\\R")) {
			final String trimmedLine = line.trim();
			if (trimmedLine.regionMatches(true, 0, "o ", 0, 2)) {
				final String objectName = trimmedLine.substring(2).trim();
				if (!StringUtils.isBlank(objectName) && !StringUtils.equalsIgnoreCase(objectName, DEFAULT_OBJ_GROUP)) {
					objectNames.add(objectName);
				}
			}
		}

		if (objectNames.size() <= 1) {
			return false;
		}

		for (final String partName : modelProperties.getAllPartNames()) {
			if (objectNames.contains(partName)) {
				return true;
			}
		}
		return false;
	}

	private ObjectArrayList<ObjectMaterialPair> parseObjectMaterialPairs(String objContents, Object2ObjectOpenHashMap<String, String> materialToTexture) {
		final ObjectArrayList<ObjectMaterialPair> objectMaterialPairs = new ObjectArrayList<>();
		String currentObjectName = DEFAULT_OBJ_GROUP;

		for (final String line : objContents.split("\\R")) {
			final String trimmedLine = line.trim();
			if (trimmedLine.regionMatches(true, 0, "o ", 0, 2)) {
				final String objectName = trimmedLine.substring(2).trim();
				currentObjectName = StringUtils.isBlank(objectName) ? DEFAULT_OBJ_GROUP : objectName;
			} else if (trimmedLine.regionMatches(true, 0, "usemtl ", 0, 7) && !StringUtils.equalsIgnoreCase(currentObjectName, DEFAULT_OBJ_GROUP)) {
				final String materialName = trimmedLine.substring(7).trim();
				final String textureName = normalizeTextureName(materialToTexture.get(materialName));
				objectMaterialPairs.add(new ObjectMaterialPair(currentObjectName, materialName, textureName));
			}
		}

		return objectMaterialPairs;
	}

	private Object2ObjectOpenHashMap<String, String> parseMaterialToTextureMap(String objIdentifier, String objContents) {
		final Object2ObjectOpenHashMap<String, String> materialToTexture = new Object2ObjectOpenHashMap<>();
		final ObjectLinkedOpenHashSet<String> mtlNames = new ObjectLinkedOpenHashSet<>();
		for (final String line : objContents.split("\\R")) {
			final String trimmedLine = line.trim();
			if (trimmedLine.regionMatches(true, 0, "mtllib ", 0, 7)) {
				final String mtlName = trimmedLine.substring(7).trim();
				if (!StringUtils.isBlank(mtlName)) {
					mtlNames.add(mtlName);
				}
			}
		}

		mtlNames.forEach(mtlName -> {
			String currentMaterial = null;
			for (final String line : getMtlContents(objIdentifier, mtlName).split("\\R")) {
				final String trimmedLine = line.trim();
				if (trimmedLine.regionMatches(true, 0, "newmtl ", 0, 7)) {
					currentMaterial = trimmedLine.substring(7).trim();
				} else if (trimmedLine.regionMatches(true, 0, "map_kd ", 0, 7) && currentMaterial != null) {
					materialToTexture.put(currentMaterial, trimmedLine.substring(7).trim());
				}
			}
		});

		return materialToTexture;
	}

	private void logReconstructionResult(String objIdentifier, int matchedMeshCount, int rawMeshCount, boolean reconstructionMatched, Object2ObjectAVLTreeMap<String, OptimizedModel.ObjModel> rebuiltObjModels, Object2ObjectOpenHashMap<String, ObjectLinkedOpenHashSet<String>> reconstructedTexturesByObject, ModelProperties modelProperties, boolean fallbackUsed) {
		if (!StringUtils.contains(objIdentifier, DEBUG_OBJ_PATH)) {
			return;
		}

		final ObjectLinkedOpenHashSet<String> matchedGroups = new ObjectLinkedOpenHashSet<>();
		modelProperties.getAllPartNames().forEach(partName -> {
			if (rebuiltObjModels.containsKey(partName)) {
				matchedGroups.add(partName);
			}
		});
		Init.LOGGER.info("[MTR OBJ DEBUG] object reconstruction matched={}", reconstructionMatched && matchedMeshCount == rawMeshCount);
		Init.LOGGER.info("[MTR OBJ DEBUG] reconstructed groups count={} sample={}", rebuiltObjModels.size(), sampleMapKeys(rebuiltObjModels));
		Init.LOGGER.info("[MTR OBJ DEBUG] modelProperties matched groups count={} sample={}", matchedGroups.size(), sampleSet(matchedGroups));
		Init.LOGGER.info("[MTR OBJ DEBUG] legacy default fallback used={}", fallbackUsed || matchedGroups.isEmpty());
		Init.LOGGER.info("[MTR DISPLAY DEBUG] reconstructed has group display={}", rebuiltObjModels.containsKey("display"));
		Init.LOGGER.info("[MTR DISPLAY DEBUG] reconstructed has group intdisplay={}", rebuiltObjModels.containsKey("intdisplay"));
		final List<?> displayMeshes = rebuiltObjModels.containsKey("display") ? getRawMeshes(rebuiltObjModels.get("display")) : null;
		Init.LOGGER.info("[MTR DISPLAY DEBUG] reconstructed display meshes={}", displayMeshes == null ? 0 : displayMeshes.size());
		Init.LOGGER.info("[MTR DISPLAY DEBUG] reconstructed display textures={}", reconstructedTexturesByObject.getOrDefault("display", new ObjectLinkedOpenHashSet<>()));
		modelProperties.iterateParts(modelPropertiesPart -> {
			if (modelPropertiesPart.isDisplayPart() || modelPropertiesPart.getDebugNames().stream().anyMatch(name -> StringUtils.containsIgnoreCase(name, "display") || StringUtils.containsIgnoreCase(name, "matrix") || StringUtils.containsIgnoreCase(name, "lcd") || StringUtils.containsIgnoreCase(name, "led") || StringUtils.containsIgnoreCase(name, "destination") || StringUtils.containsIgnoreCase(name, "ziel"))) {
				Init.LOGGER.info("[MTR DISPLAY DEBUG] modelProperties display candidate name={} type={} renderStage={} condition={}", modelPropertiesPart.getDebugNames(), modelPropertiesPart.getDebugType(), modelPropertiesPart.getDebugRenderStage(), modelPropertiesPart.getDebugCondition());
			}
		});
	}

	private static ObjectArrayList<String> sampleMapKeys(Object2ObjectAVLTreeMap<String, ?> map) {
		return sampleSet(new ObjectLinkedOpenHashSet<>(map.keySet()));
	}

	private static int findMatchingPairIndex(ObjectArrayList<ObjectMaterialPair> objectMaterialPairs, int startIndex, String meshTextureName) {
		if (StringUtils.isBlank(meshTextureName)) {
			return -1;
		}

		for (int i = startIndex; i < objectMaterialPairs.size(); i++) {
			if (StringUtils.equals(meshTextureName, objectMaterialPairs.get(i).textureName)) {
				return i;
			}
		}
		return -1;
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

	@Nullable
	private static OptimizedModel.ObjModel instantiateFilteredObjModel(ObjectArrayList<Object> selectedRawMeshes) {
		if (OBJ_MODEL_CONSTRUCTOR == null || selectedRawMeshes.isEmpty()) {
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

	private static String getMeshTextureName(Object rawMesh) {
		try {
			final Object materialProperties = rawMesh.getClass().getField("materialProperties").get(rawMesh);
			return ((org.mtr.mapping.render.batch.MaterialProperties) materialProperties).getTexture().data.toString();
		} catch (Exception e) {
			return "";
		}
	}

	private static String normalizeTextureName(@Nullable String textureName) {
		if (StringUtils.isBlank(textureName)) {
			return "";
		}
		final int slashIndex = Math.max(textureName.lastIndexOf('/'), textureName.lastIndexOf('\\'));
		return (slashIndex >= 0 ? textureName.substring(slashIndex + 1) : textureName).toLowerCase(Locale.ROOT);
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


	private void logRawObjAndModelProperties(String objIdentifier, String objContents, ModelProperties modelProperties) {
		if (!StringUtils.contains(objIdentifier, DEBUG_OBJ_PATH) || !LOGGED_DEBUG_KEYS.add("raw_obj_" + objIdentifier)) {
			return;
		}

		final ObjectLinkedOpenHashSet<String> objectNames = new ObjectLinkedOpenHashSet<>();
		final ObjectLinkedOpenHashSet<String> groupNames = new ObjectLinkedOpenHashSet<>();
		final ObjectLinkedOpenHashSet<String> materialNames = new ObjectLinkedOpenHashSet<>();
		int objectCount = 0;
		int groupCount = 0;
		int useMtlCount = 0;

		for (final String line : objContents.split("\\R")) {
			final String trimmedLine = line.trim();
			final String lowercaseLine = trimmedLine.toLowerCase(Locale.ROOT);
			if (lowercaseLine.startsWith("o ")) {
				objectCount++;
				objectNames.add(trimmedLine.substring(2).trim());
			} else if (lowercaseLine.startsWith("g ")) {
				groupCount++;
				groupNames.add(trimmedLine.substring(2).trim());
			} else if (lowercaseLine.startsWith("usemtl ")) {
				useMtlCount++;
				materialNames.add(trimmedLine.substring(7).trim());
			}
		}

		final ObjectLinkedOpenHashSet<String> modelPropertiesPartNames = modelProperties.getAllPartNames();
		final ObjectLinkedOpenHashSet<String> rawNameMatches = new ObjectLinkedOpenHashSet<>();
		modelPropertiesPartNames.forEach(partName -> {
			if (objectNames.contains(partName) || groupNames.contains(partName)) {
				rawNameMatches.add(partName);
			}
		});

		Init.LOGGER.info("[MTR OBJ DEBUG] raw lines object=o count={} sample={}", objectCount, sampleSet(objectNames));
		Init.LOGGER.info("[MTR OBJ DEBUG] raw lines group=g count={} sample={}", groupCount, sampleSet(groupNames));
		Init.LOGGER.info("[MTR OBJ DEBUG] raw lines usemtl count={} sample={}", useMtlCount, sampleSet(materialNames));
		Init.LOGGER.info("[MTR OBJ DEBUG] raw object names={}", sampleSet(objectNames));
		Init.LOGGER.info("[MTR OBJ DEBUG] raw group names={}", sampleSet(groupNames));
		Init.LOGGER.info("[MTR OBJ DEBUG] raw material names={}", sampleSet(materialNames));
		Init.LOGGER.info("[MTR OBJ DEBUG] modelProperties part names count={} sample={}", modelPropertiesPartNames.size(), sampleSet(modelPropertiesPartNames));
		Init.LOGGER.info("[MTR OBJ DEBUG] raw-name matches modelProperties count={} sample={}", rawNameMatches.size(), sampleSet(rawNameMatches));
	}

	private static ObjectArrayList<String> sampleSet(ObjectLinkedOpenHashSet<String> values) {
		final ObjectArrayList<String> sample = new ObjectArrayList<>();
		int count = 0;
		for (final String value : values) {
			sample.add(value);
			count++;
			if (count >= 12) {
				break;
			}
		}
		return sample;
	}

	private String getMtlContents(String objIdentifier, String mtlString) {
		final String mtlContents = resourceProvider.get(CustomResourceTools.getResourceFromSamePath(modelResource, mtlString, "mtl"));
		if (StringUtils.contains(objIdentifier, DEBUG_OBJ_PATH) && LOGGED_DEBUG_KEYS.add("mtl_" + objIdentifier)) {
			final ObjectLinkedOpenHashSet<String> materialNames = new ObjectLinkedOpenHashSet<>();
			final ObjectLinkedOpenHashSet<String> textureNames = new ObjectLinkedOpenHashSet<>();
			for (final String line : mtlContents.split("\\R")) {
				final String trimmedLine = line.trim();
				final String lowercaseLine = trimmedLine.toLowerCase(Locale.ROOT);
				if (lowercaseLine.startsWith("newmtl ")) {
					materialNames.add(trimmedLine.substring(7).trim());
				} else if (lowercaseLine.startsWith("map_kd ")) {
					textureNames.add(trimmedLine.substring(7).trim());
				}
			}
			Init.LOGGER.info("[MTR OBJ DEBUG] mtl materials={}", materialNames);
			Init.LOGGER.info("[MTR OBJ DEBUG] mtl textures={}", textureNames);
		}
		return mtlContents;
	}

	private static final class ObjectMaterialPair {
		private final String objectName;
		private final String materialName;
		private final String textureName;

		private ObjectMaterialPair(String objectName, String materialName, String textureName) {
			this.objectName = objectName;
			this.materialName = materialName;
			this.textureName = textureName;
		}
	}
}
