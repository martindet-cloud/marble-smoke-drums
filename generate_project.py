#!/usr/bin/env python3
"""Regenerate the self-contained Xcode project without external dependencies."""
from pathlib import Path

root = Path(__file__).resolve().parent
project = root / "MarbleSmokeDrums.xcodeproj"
project.mkdir(exist_ok=True)
swift = ["DrumsApp.swift", "DrumMachine.swift", "DrumView.swift"]
sounds = ["Kick", "Snare", "Clap", "ClosedHat", "OpenHat", "Rim", "Tom", "Perc"]
objects = []

def ident(n): return f"D4A500000000000000{n:06X}"
def add(n, body): objects.append(f"\t\t{ident(n)} = {{ {body} }};")

add(1, "isa = PBXProject; attributes = { LastUpgradeCheck = 1500; TargetAttributes = { " + ident(2) + " = { CreatedOnToolsVersion = 15.0; }; }; }; buildConfigurationList = " + ident(6) + "; compatibilityVersion = \"Xcode 14.0\"; developmentRegion = en; hasScannedForEncodings = 0; knownRegions = (en, Base); mainGroup = " + ident(3) + "; productRefGroup = " + ident(4) + "; projectDirPath = \"\"; projectRoot = \"\"; targets = (" + ident(2) + ",);")
add(2, "isa = PBXNativeTarget; buildConfigurationList = " + ident(7) + "; buildPhases = (" + ident(8) + ", " + ident(9) + ", " + ident(10) + "); buildRules = (); dependencies = (); name = MarbleSmokeDrums; productName = MarbleSmokeDrums; productReference = " + ident(5) + "; productType = \"com.apple.product-type.application\";")
add(3, "isa = PBXGroup; children = (" + ",".join(ident(n) for n in [11, 4]) + ",); sourceTree = \"<group>\";")
add(4, "isa = PBXGroup; children = (" + ident(5) + ",); name = Products; sourceTree = \"<group>\";")
add(5, "isa = PBXFileReference; explicitFileType = wrapper.application; includeInIndex = 0; path = MarbleSmokeDrums.app; sourceTree = BUILT_PRODUCTS_DIR;")
add(6, "isa = XCConfigurationList; buildConfigurations = (" + ident(40) + ", " + ident(41) + "); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;")
add(7, "isa = XCConfigurationList; buildConfigurations = (" + ident(42) + ", " + ident(43) + "); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;")
add(14, "isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = \"<group>\";")
add(120, "isa = PBXBuildFile; fileRef = " + ident(14) + ";")
add(8, "isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = (" + ",".join(ident(100+i) for i in range(3)) + ",); runOnlyForDeploymentPostprocessing = 0;")
add(9, "isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = (" + ",".join([ident(110+i) for i in range(8)] + [ident(120)]) + ",); runOnlyForDeploymentPostprocessing = 0;")
add(10, "isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;")
add(11, "isa = PBXGroup; children = (" + ",".join(ident(20+i) for i in range(3)) + "," + ident(14) + "," + ident(13) + ",); path = DrumsApp; sourceTree = \"<group>\";")
add(13, "isa = PBXGroup; children = (" + ",".join(ident(30+i) for i in range(8)) + ",); path = Resources; sourceTree = \"<group>\";")
for i, name in enumerate(swift):
    add(20+i, f"isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = {name}; sourceTree = \"<group>\";")
    add(100+i, f"isa = PBXBuildFile; fileRef = {ident(20+i)};")
for i, name in enumerate(sounds):
    add(30+i, f"isa = PBXFileReference; lastKnownFileType = audio.wav; path = {name}.wav; sourceTree = \"<group>\";")
    add(110+i, f"isa = PBXBuildFile; fileRef = {ident(30+i)};")

base = "{ ALWAYS_SEARCH_USER_PATHS = NO; CLANG_ENABLE_MODULES = YES; CLANG_ENABLE_OBJC_ARC = YES; GCC_OPTIMIZATION_LEVEL = 0; IPHONEOS_DEPLOYMENT_TARGET = 17.0; SDKROOT = iphoneos; SWIFT_VERSION = 5.0; TARGETED_DEVICE_FAMILY = 1; }"
debug = "{ ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon; CODE_SIGN_STYLE = Automatic; CURRENT_PROJECT_VERSION = 1; DEVELOPMENT_TEAM = \"\"; GENERATE_INFOPLIST_FILE = NO; LD_RUNPATH_SEARCH_PATHS = \"$(inherited) @executable_path/Frameworks\"; MARKETING_VERSION = 0.1; INFOPLIST_FILE = DrumsApp/Info.plist; IPHONEOS_DEPLOYMENT_TARGET = 17.0; PRODUCT_BUNDLE_IDENTIFIER = com.marblesmoke.drums.pocket; PRODUCT_NAME = MarbleSmokeDrums; SWIFT_VERSION = 5.0; TARGETED_DEVICE_FAMILY = 1; }"
for n, name, settings in [(40, "Debug", base), (41, "Release", base.replace("GCC_OPTIMIZATION_LEVEL = 0", "GCC_OPTIMIZATION_LEVEL = s")), (42, "Debug", debug), (43, "Release", debug)]:
    add(n, f"isa = XCBuildConfiguration; buildSettings = {settings}; name = {name};")

content = "// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n" + "\n".join(objects) + "\n}; rootObject = " + ident(1) + "; }\n"
(project / "project.pbxproj").write_text(content)
print(project / "project.pbxproj")
