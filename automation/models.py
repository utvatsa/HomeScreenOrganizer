"""Pydantic data models for declarative layout, observed state, inventory, and execution."""

from __future__ import annotations

from typing import Literal, Optional
from pydantic import BaseModel, Field


class GridSpec(BaseModel):
    columns: int = Field(ge=3, le=7)
    rows: Optional[int] = Field(default=6, ge=4, le=8)


class LauncherConfig(BaseModel):
    type: str = "samsung-one-ui"
    preserveExistingWallpaper: bool = True
    grid: GridSpec = Field(default_factory=lambda: GridSpec(columns=4, rows=6))


class HeaderTheme(BaseModel):
    fontFamily: str = "sans-serif"
    fontWeight: int = 300
    fontSizeSp: float = 24.0
    textColor: str = "#FFFFFFFF"
    opacity: float = Field(default=0.92, ge=0.0, le=1.0)
    alignment: Literal["start", "center", "end"] = "start"
    backgroundColor: str = "#00000000"
    paddingDp: int = 10
    letterSpacing: float = 0.04
    showSeparator: bool = False
    separatorColor: str = "#40FFFFFF"
    shadowEnabled: bool = False


class ThemeConfig(BaseModel):
    header: HeaderTheme = Field(default_factory=HeaderTheme)


class WallpaperConfig(BaseModel):
    mode: Literal["preserve", "custom"] = "preserve"
    source: Optional[str] = None
    checksum: Optional[str] = None
    type: Optional[str] = None


class PositionSpec(BaseModel):
    row: int = Field(ge=0)
    column: int = Field(ge=0)
    rowSpan: int = Field(default=1, ge=1)
    columnSpan: int = Field(default=1, ge=1)


class HeaderInstanceConfig(BaseModel):
    enabled: bool = True
    inheritTheme: bool = True
    subtitle: Optional[str] = None
    position: PositionSpec = Field(default_factory=lambda: PositionSpec(row=0, column=0, rowSpan=1, columnSpan=4))
    overrides: Optional[dict[str, object]] = None


class FolderChildSpec(BaseModel):
    id: str
    packageName: str
    activityName: Optional[str] = None
    label: str
    profileId: int = 0


class PageItemSpec(BaseModel):
    id: str
    type: Literal[
        "app",
        "pinned_shortcut",
        "folder",
        "header_widget",
        "widget",
        "placeholder_widget",
    ]
    title: Optional[str] = None
    packageName: Optional[str] = None
    activityName: Optional[str] = None
    provider: Optional[str] = None
    profileId: int = 0
    position: PositionSpec
    children: list[FolderChildSpec] = Field(default_factory=list)


class DockConfig(BaseModel):
    mode: Literal["preserve", "custom"] = "preserve"
    items: list[PageItemSpec] = Field(default_factory=list)


class PageSpec(BaseModel):
    id: str
    title: Optional[str] = None
    order: int = Field(ge=0)
    isDefaultHome: bool = False
    retainEmptyPage: bool = False
    header: HeaderInstanceConfig = Field(default_factory=HeaderInstanceConfig)
    items: list[PageItemSpec] = Field(default_factory=list)


class DesiredLayout(BaseModel):
    schemaVersion: int = 1
    launcher: LauncherConfig = Field(default_factory=LauncherConfig)
    theme: ThemeConfig = Field(default_factory=ThemeConfig)
    wallpaper: WallpaperConfig = Field(default_factory=WallpaperConfig)
    dock: DockConfig = Field(default_factory=DockConfig)
    pages: list[PageSpec] = Field(default_factory=list)


class ApplicationEntry(BaseModel):
    id: str
    displayName: str
    packageName: str
    activityName: str
    componentName: str
    versionName: Optional[str] = None
    versionCode: Optional[int] = None
    isSystemApp: bool = False
    enabled: bool = True
    launcherVisible: bool = True
    profileId: int = 0
    installerPackage: Optional[str] = None
    iconPath: Optional[str] = None
    existingHomePages: list[int] = Field(default_factory=list)
    existingFolders: list[str] = Field(default_factory=list)
    proposedCategories: list[str] = Field(default_factory=list)
    primaryCategory: Optional[str] = None
    bankInstitution: Optional[str] = None
    confidence: float = 0.0
    rationale: str = ""
    userOverride: Optional[dict[str, object]] = None
