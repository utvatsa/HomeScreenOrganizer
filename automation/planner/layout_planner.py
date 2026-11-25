"""Layout planning engine for 8-icon-max, one-hand-accessible, suite-organized home screens."""

from __future__ import annotations

from typing import Any
from automation.models import (
    DesiredLayout,
    DockConfig,
    FolderChildSpec,
    HeaderInstanceConfig,
    HeaderTheme,
    LauncherConfig,
    GridSpec,
    PageItemSpec,
    PageSpec,
    PositionSpec,
    ThemeConfig,
    WallpaperConfig,
)


def _index_apps(apps: list[dict[str, Any]]) -> tuple[dict[str, dict[str, Any]], dict[str, list[dict[str, Any]]]]:
    by_id = {a["id"]: a for a in apps}
    by_pkg: dict[str, list[dict[str, Any]]] = {}
    for a in apps:
        by_pkg.setdefault(a["packageName"], []).append(a)
    return by_id, by_pkg


def _resolve_app(
    ref: str,
    by_id: dict[str, dict[str, Any]],
    by_pkg: dict[str, list[dict[str, Any]]],
) -> dict[str, Any] | None:
    if ref in by_id:
        return by_id[ref]
    candidates = by_pkg.get(ref, [])
    if candidates:
        return sorted(candidates, key=lambda a: a["profileId"])[0]
    return None


# Curated suite & category page blueprint enforcing <= 8 items per page in bottom 2 rows (rows 4-5 on 4x6)
PAGE_BLUEPRINTS: list[dict[str, Any]] = [
    {
        "id": "daily-essentials",
        "title": "Daily Essentials",
        "isDefaultHome": True,
        "weatherWidget": True,
        "slots": [
            {"kind": "app", "ref": "u0:com.whatsapp/com.whatsapp.Main"},
            {"kind": "app", "ref": "u95:com.whatsapp/com.whatsapp.Main"},
            {"kind": "app", "ref": "com.android.chrome"},
            {"kind": "app", "ref": "com.google.android.gm"},
            {"kind": "app", "ref": "com.microsoft.office.outlook"},
            {"kind": "app", "ref": "com.google.android.calendar"},
            {"kind": "app", "ref": "com.samsung.android.app.notes"},
            {
                "kind": "folder",
                "slug": "google-suite",
                "title": "Google Suite",
                "refs": [
                    "u0:com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity",
                    "com.google.android.apps.docs",
                    "com.google.android.apps.docs.editors.docs",
                    "com.google.android.apps.docs.editors.sheets",
                    "com.google.android.keep",
                    "com.google.android.apps.photos",
                    "com.google.android.apps.tachyon",
                    "com.google.android.apps.tasks",
                    "com.google.android.apps.maps",
                    "com.google.android.youtube",
                    "com.google.android.apps.youtube.music",
                    "com.google.android.videos",
                    "com.google.android.apps.books",
                ],
            },
        ],
    },
    {
        "id": "payments",
        "title": "Payments",
        "slots": [
            {"kind": "app", "ref": "com.dreamplug.androidapp"},
            {"kind": "app", "ref": "com.google.android.apps.nbu.paisa.user"},
            {"kind": "app", "ref": "com.phonepe.app"},
            {"kind": "app", "ref": "net.one97.paytm"},
            {"kind": "app", "ref": "in.org.npci.upiapp"},
            {"kind": "app", "ref": "u0:in.amazon.mShop.android.shopping/com.amazon.mShop.amazonpay.AmazonPayHomeActivityAliasWoShortcuts"},
            {"kind": "app", "ref": "com.samsung.android.spay"},
            {
                "kind": "folder",
                "slug": "more-payments",
                "title": "More Payments",
                "refs": [
                    "com.google.android.apps.walletnfcrel",
                    "com.mobikwik_new",
                    "com.naviapp",
                    "com.myairtelapp",
                    "money.super.payments",
                    "com.fastaguser",
                    "com.savesage.club",
                    "com.Splitwise.SplitwiseMobile",
                    "org.altruist.BajajExperia",
                    "com.hdfcbank.payzapp",
                    "org.chromium.webapk.ab724fdcaff993889_v2",
                    "com.ge.capital.konysbiapp",
                    "com.freedomrewardz",
                    "com.sbi.upi",
                ],
            },
        ],
    },
    {
        "id": "banking-suites",
        "title": "Banking Suites",
        "slots": [
            {
                "kind": "folder",
                "slug": "sbi-suite",
                "title": "SBI Suite",
                "refs": [
                    "com.sbi.lotusintouch",
                    "com.sbi.SBIFreedomPlus",
                    "com.sbi.upi",
                    "com.ge.capital.konysbiapp",
                    "com.sbi.authenticator",
                    "com.freedomrewardz",
                    "com.sbig.insurance",
                ],
            },
            {
                "kind": "folder",
                "slug": "hdfc-suite",
                "title": "HDFC Suite",
                "refs": [
                    "com.hdfcbank.android.now",
                    "com.hdfcbank.payzapp",
                    "org.chromium.webapk.ab724fdcaff993889_v2",
                ],
            },
            {"kind": "app", "ref": "com.csam.icici.bank.imobile"},
            {"kind": "app", "ref": "com.axis.mobile"},
            {
                "kind": "folder",
                "slug": "insurances-suite",
                "title": "Insurances",
                "refs": [
                    "com.acko.android",
                    "com.policybazaar",
                    "com.star.customer_app",
                    "com.getvisitapp.android",
                    "com.prudentplus.app",
                    "com.sbig.insurance",
                    "com.lic.customer.superapp",
                    "com.lic.liccustomer",
                    "com.ba.cp.controller",
                ],
            },
        ],
    },
    {
        "id": "upi",
        "title": "UPI",
        "slots": [
            {"kind": "app", "ref": "com.google.android.apps.nbu.paisa.user"},
            {"kind": "app", "ref": "com.phonepe.app"},
            {"kind": "app", "ref": "net.one97.paytm"},
            {"kind": "app", "ref": "in.org.npci.upiapp"},
            {"kind": "app", "ref": "com.dreamplug.androidapp"},
            {"kind": "app", "ref": "u0:in.amazon.mShop.android.shopping/com.amazon.mShop.amazonpay.AmazonPayHomeActivityAliasWoShortcuts"},
            {
                "kind": "folder",
                "slug": "bank-upi",
                "title": "Bank UPI",
                "refs": [
                    "com.sbi.upi",
                    "com.hdfcbank.android.now",
                    "com.hdfcbank.payzapp",
                    "com.csam.icici.bank.imobile",
                    "com.axis.mobile",
                ],
            },
            {
                "kind": "folder",
                "slug": "upi-wallets",
                "title": "UPI Suite",
                "refs": [
                    "com.mobikwik_new",
                    "com.naviapp",
                    "com.myairtelapp",
                    "money.super.payments",
                    "com.samsung.android.spay",
                ],
            },
        ],
    },
    {
        "id": "finance-wealth",
        "title": "Finance & Wealth",
        "slots": [
            {"kind": "app", "ref": "com.zerodha.kite3"},
            {"kind": "app", "ref": "com.nextbillion.groww"},
            {"kind": "app", "ref": "in.indwealth"},
            {"kind": "app", "ref": "com.smallcase.android"},
            {"kind": "app", "ref": "com.pennywiseai.tracker"},
            {"kind": "app", "ref": "com.daamitt.walnut.app"},
            {
                "kind": "folder",
                "slug": "investing-crypto",
                "title": "Invest & Crypto",
                "refs": [
                    "in.tickertape",
                    "com.cdsl.myeasi",
                    "com.dezerv",
                    "com.fourdegreewater.wintwealth",
                    "money.powerup.uni.invest.app",
                    "com.ofaclientapp",
                    "com.coindcx",
                    "com.coindcx.btc",
                    "com.coinswitch.kuber",
                    "com.bitfinex.mobileapp",
                    "app.phantom",
                ],
            },
            {
                "kind": "folder",
                "slug": "credit-tax",
                "title": "Credit & Bills",
                "refs": [
                    "com.Splitwise.SplitwiseMobile",
                    "com.dreamplug.androidapp",
                    "com.paisabazaar",
                    "com.savesage.club",
                ],
            },
        ],
    },
    {
        "id": "food-dining",
        "title": "Food & Dining",
        "slots": [
            {"kind": "app", "ref": "in.swiggy.android"},
            {"kind": "app", "ref": "com.application.zomato"},
            {"kind": "app", "ref": "com.grofers.customerapp"},
            {"kind": "app", "ref": "com.zeptoconsumerapp"},
            {"kind": "app", "ref": "com.bigbasket.mobileapp"},
            {"kind": "app", "ref": "com.application.zomato.district"},
            {"kind": "app", "ref": "com.Dominos"},
            {
                "kind": "folder",
                "slug": "quick-groceries",
                "title": "Groceries",
                "refs": [
                    "com.grofers.customerapp.lit",
                    "com.raincan.android.hybrid",
                    "com.firstclub.app",
                    "com.licious",
                    "com.ownly.customer",
                    "u0:in.amazon.mShop.android.shopping/com.amazon.mShop.amazonnow.AmazonNowHomeActivityAlias",
                ],
            },
        ],
    },
    {
        "id": "travel-cabs",
        "title": "Travel & Cabs",
        "slots": [
            {"kind": "app", "ref": "com.ubercab"},
            {"kind": "app", "ref": "com.rapido.passenger"},
            {"kind": "app", "ref": "in.juspay.nammayatri"},
            {"kind": "app", "ref": "com.aum.nammametro"},
            {"kind": "app", "ref": "com.google.android.apps.maps"},
            {"kind": "app", "ref": "com.nic.mparivahan"},
            {"kind": "app", "ref": "com.fastaguser"},
            {
                "kind": "folder",
                "slug": "vehicle-transit",
                "title": "Auto & Transit",
                "refs": [
                    "in.juspay.jatrisaathi",
                    "com.google.earth",
                    "com.cuvora.carinfo",
                    "com.vehicle.rto.vahan.status.information.register",
                    "com.hmil.myhyundai",
                    "com.mychoize.cars",
                    "com.selfdrive",
                    "com.spinny.consumer",
                ],
            },
        ],
    },
    {
        "id": "travel-bookings",
        "title": "Travel & Bookings",
        "slots": [
            {"kind": "app", "ref": "com.makemytrip"},
            {"kind": "app", "ref": "com.goibibo"},
            {"kind": "app", "ref": "in.goindigo.android"},
            {"kind": "app", "ref": "cris.org.in.prs.ima"},
            {"kind": "app", "ref": "com.booking"},
            {"kind": "app", "ref": "org.digiyatra.org"},
            {
                "kind": "folder",
                "slug": "flights-visas",
                "title": "Flights & Visa",
                "refs": [
                    "com.airindiaexpress.AIX",
                    "com.akasaair.ibe",
                    "com.cleartrip.android",
                    "com.easemytrip.android",
                    "com.ixigo",
                    "com.yatra.base",
                    "net.skyscanner.android.main",
                    "com.atlys.android",
                    "com.prioritypass3",
                ],
            },
            {
                "kind": "folder",
                "slug": "trains-stays",
                "title": "Trains & Stays",
                "refs": [
                    "com.whereismytrain.android",
                    "com.ixigo.train.ixitrain",
                    "com.confirmtkt.lite",
                    "in.redbus.android",
                    "com.airbnb.android",
                    "com.klook",
                    "net.one97.travel",
                ],
            },
        ],
    },
    {
        "id": "ai-suite",
        "title": "AI Suite",
        "slots": [
            {"kind": "app", "ref": "com.openai.chatgpt"},
            {"kind": "app", "ref": "com.anthropic.claude"},
            {"kind": "app", "ref": "com.google.android.apps.bard"},
            {"kind": "app", "ref": "ai.perplexity.app.android"},
            {"kind": "app", "ref": "ai.x.grok"},
            {"kind": "app", "ref": "com.microsoft.copilot"},
            {
                "kind": "folder",
                "slug": "more-ai",
                "title": "AI Tools",
                "refs": [
                    "com.moonshot.kimichat",
                    "ai.x.grok.bot",
                    "ai.sol.opencode",
                    "org.chromium.webapk.ae6c0d081b0fa7acf_v2",
                    "u0:com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity",
                ],
            },
        ],
    },
    {
        "id": "productivity-work",
        "title": "Productivity",
        "slots": [
            {"kind": "app", "ref": "com.microsoft.office.outlook"},
            {"kind": "app", "ref": "com.microsoft.teams"},
            {"kind": "app", "ref": "com.samsung.android.app.notes"},
            {"kind": "app", "ref": "com.google.android.keep"},
            {"kind": "app", "ref": "notion.id"},
            {
                "kind": "folder",
                "slug": "ms-suite",
                "title": "Microsoft Suite",
                "refs": [
                    "com.microsoft.office.outlook",
                    "com.microsoft.teams",
                    "com.microsoft.office.officehubrow",
                    "com.microsoft.office.onenote",
                    "com.microsoft.sharepoint",
                    "com.microsoft.skydrive",
                    "com.microsoft.azure",
                    "com.microsoft.windowsintune.companyportal",
                ],
            },
            {
                "kind": "folder",
                "slug": "auth-security",
                "title": "Auth & Docs",
                "refs": [
                    "com.google.android.apps.authenticator2",
                    "com.azure.authenticator",
                    "com.vasco.digipass.es",
                    "com.kunzisoft.keepass.free",
                    "com.adobe.reader",
                    "com.adobe.scan.android",
                    "com.todoist",
                    "com.ticktick.task",
                ],
            },
            {
                "kind": "folder",
                "slug": "dev-work",
                "title": "Work & Dev",
                "refs": [
                    "com.atlassian.android.jira.core",
                    "com.github.android",
                    "com.google.android.apps.cloudconsole",
                    "com.linkedin.android",
                    "com.linkedin.android.learning",
                    "com.darwinbox.darwinbox",
                    "com.google.android.apps.dynamite",
                ],
            },
        ],
    },
    {
        "id": "health-fitness",
        "title": "Health & Fitness",
        "slots": [
            {"kind": "app", "ref": "com.sec.android.app.shealth"},
            {"kind": "app", "ref": "com.garmin.android.apps.connectmobile"},
            {"kind": "app", "ref": "com.strava"},
            {"kind": "app", "ref": "fit.cure.android"},
            {"kind": "app", "ref": "com.apollo.patientapp"},
            {
                "kind": "folder",
                "slug": "garmin-suite",
                "title": "Garmin Suite",
                "refs": [
                    "com.garmin.android.apps.connectmobile",
                    "com.garmin.connectiq",
                    "com.garmin.android.apps.explore",
                    "com.garmin.android.apps.gccm",
                ],
            },
            {
                "kind": "folder",
                "slug": "workouts",
                "title": "Workouts",
                "refs": [
                    "com.squats.fittr",
                    "com.myfitnesspal.android",
                    "com.healthifyme.basic",
                    "com.google.android.apps.fitness",
                    "com.fitbit.FitbitMobile",
                    "com.nike.omega",
                    "je.fit",
                    "fitness.online.app",
                    "musclemonster.fitness.workout.home.gym.planner",
                    "homeworkout.homeworkouts.noequipment",
                    "com.divessi.ssi",
                    "apps.r.compass",
                ],
            },
            {
                "kind": "folder",
                "slug": "medical-wellness",
                "title": "Care & Labs",
                "refs": [
                    "com.aranoah.healthkart.plus",
                    "com.practo.fabric",
                    "com.manipal.manipallifeson",
                    "com.ewa.tpa",
                    "com.evamall.evacustomer",
                    "com.example.smoketrack",
                ],
            },
        ],
    },
    {
        "id": "smart-home",
        "title": "Smart Home",
        "slots": [
            {"kind": "app", "ref": "com.google.android.apps.chromecast.app"},
            {"kind": "app", "ref": "com.samsung.android.oneconnect"},
            {"kind": "app", "ref": "com.hero.iot"},
            {"kind": "app", "ref": "com.wizconnected.wiz2"},
            {"kind": "app", "ref": "com.atomberg.app"},
            {"kind": "app", "ref": "com.tuya.smartlife"},
            {"kind": "app", "ref": "com.act.mobile.apps"},
            {
                "kind": "folder",
                "slug": "more-smart-home",
                "title": "Home Devices",
                "refs": [
                    "homemate.smart",
                    "com.tcl.tclhome",
                    "com.nobroker.app",
                    "com.mygate.user",
                ],
            },
        ],
    },
    {
        "id": "shopping",
        "title": "Shopping",
        "slots": [
            {"kind": "app", "ref": "u0:in.amazon.mShop.android.shopping/com.amazon.mShop.home.HomeActivity"},
            {"kind": "app", "ref": "com.flipkart.android"},
            {"kind": "app", "ref": "com.myntra.android"},
            {"kind": "app", "ref": "com.fsn.nykaa"},
            {"kind": "app", "ref": "com.ril.ajio"},
            {"kind": "app", "ref": "com.ingka.ikea.app"},
            {
                "kind": "folder",
                "slug": "fashion-lifestyle",
                "title": "Fashion",
                "refs": [
                    "com.uniqlo.in.catalogue",
                    "com.hm.goe",
                    "com.adidas.app",
                    "com.puma.ecom.app",
                    "com.nike.india",
                    "com.thesouledstore",
                    "com.tataunistore.luxury",
                    "com.lenskart.app",
                    "fc.admin.fcexpressadmin",
                ],
            },
            {
                "kind": "folder",
                "slug": "deals-resale",
                "title": "Deals & Shop",
                "refs": [
                    "com.meesho.supply",
                    "com.samsung.ecomm.global.in",
                    "com.reglobe.cashify",
                    "com.olx.southasia",
                ],
            },
        ],
    },
    {
        "id": "entertainment",
        "title": "Entertainment",
        "slots": [
            {"kind": "app", "ref": "com.google.android.youtube"},
            {"kind": "app", "ref": "com.netflix.mediaclient"},
            {"kind": "app", "ref": "com.amazon.avod.thirdpartyclient"},
            {"kind": "app", "ref": "in.startv.hotstar"},
            {"kind": "app", "ref": "com.spotify.music"},
            {"kind": "app", "ref": "com.google.android.apps.youtube.music"},
            {
                "kind": "folder",
                "slug": "ott-audio",
                "title": "OTT & Audio",
                "refs": [
                    "com.apple.atve.androidtv.appletv",
                    "com.sonyliv",
                    "com.graymatrix.did",
                    "com.google.android.videos",
                    "com.samsung.android.tvplus",
                    "com.apple.android.music",
                    "com.audible.application",
                ],
            },
            {
                "kind": "folder",
                "slug": "events-sports",
                "title": "Events & Play",
                "refs": [
                    "com.bt.bms",
                    "in.insider.consumer",
                    "com.cricbuzz.android",
                    "com.livescore",
                    "com.scee.psxandroid",
                    "com.samsung.android.game.gamehome",
                ],
            },
        ],
    },
    {
        "id": "government-docs",
        "title": "Government & Docs",
        "slots": [
            {"kind": "app", "ref": "in.gov.uidai.pehchaan"},
            {"kind": "app", "ref": "com.digilocker.android"},
            {"kind": "app", "ref": "gov.mea.pspv2"},
            {"kind": "app", "ref": "com.nic.mparivahan"},
            {"kind": "app", "ref": "in.gov.eci.app"},
            {"kind": "app", "ref": "org.digiyatra.org"},
        ],
    },
]


def generate_proposed_layout(
    apps: list[dict[str, Any]],
    *,
    columns: int = 4,
    rows: int = 6,
    single_app_folders_supported: bool = False,
    empty_page_retained: bool = True,
    weather_widget_on_daily: bool = True,
) -> tuple[DesiredLayout, dict[str, Any]]:
    """Build an 8-icon-max, bottom-2-rows one-handed layout with suite folders."""
    by_id, by_pkg = _index_apps(apps)
    pages: list[PageSpec] = []
    capacity_report: list[dict[str, Any]] = []

    bottom_start_row = max(1, rows - 2)  # rows 4 & 5 on 4x6 -> max 8 one-hand items!

    for order_idx, bp in enumerate(PAGE_BLUEPRINTS):
        page_id = bp["id"]
        page_title = bp["title"]
        header_cfg = HeaderInstanceConfig(
            enabled=True,
            inheritTheme=True,
            position=PositionSpec(row=0, column=0, rowSpan=1, columnSpan=columns),
        )

        items: list[PageItemSpec] = []
        if bp.get("weatherWidget") and weather_widget_on_daily:
            items.append(
                PageItemSpec(
                    id=f"{page_id}:widget:weather",
                    type="widget",
                    title="Weather",
                    provider="com.sec.android.daemonapp/.appwidget.WeatherAppWidget2x1",
                    position=PositionSpec(row=1, column=0, rowSpan=1, columnSpan=min(4, columns)),
                )
            )

        slots = bp["slots"][:8]
        for idx, slot in enumerate(slots):
            r = bottom_start_row + (idx // columns)
            c = idx % columns
            if r >= rows:
                break
            if slot["kind"] == "app":
                app = _resolve_app(slot["ref"], by_id, by_pkg)
                if not app:
                    continue
                items.append(
                    PageItemSpec(
                        id=f"{page_id}:app:{idx}:{app['id']}",
                        type="app",
                        title=app["displayName"],
                        packageName=app["packageName"],
                        activityName=app["activityName"],
                        profileId=app["profileId"],
                        position=PositionSpec(row=r, column=c, rowSpan=1, columnSpan=1),
                    )
                )
            elif slot["kind"] == "folder":
                children: list[FolderChildSpec] = []
                seen_ch = set()
                for ref in slot["refs"]:
                    app = _resolve_app(ref, by_id, by_pkg)
                    if not app or app["id"] in seen_ch:
                        continue
                    seen_ch.add(app["id"])
                    children.append(
                        FolderChildSpec(
                            id=f"{page_id}:{slot['slug']}:{app['id']}",
                            packageName=app["packageName"],
                            activityName=app["activityName"],
                            label=app["displayName"],
                            profileId=app["profileId"],
                        )
                    )
                if len(children) >= 2 or (len(children) == 1 and single_app_folders_supported):
                    items.append(
                        PageItemSpec(
                            id=f"{page_id}:folder:{slot['slug']}",
                            type="folder",
                            title=slot["title"],
                            position=PositionSpec(row=r, column=c, rowSpan=1, columnSpan=1),
                            children=children,
                        )
                    )
                elif len(children) == 1:
                    ch = children[0]
                    items.append(
                        PageItemSpec(
                            id=f"{page_id}:app:{idx}:{ch.packageName}",
                            type="app",
                            title=ch.label,
                            packageName=ch.packageName,
                            activityName=ch.activityName or "",
                            profileId=ch.profileId,
                            position=PositionSpec(row=r, column=c, rowSpan=1, columnSpan=1),
                        )
                    )

        interactive_count = sum(1 for it in items if it.type in ("app", "folder"))
        pages.append(
            PageSpec(
                id=page_id,
                title=page_title,
                order=order_idx,
                isDefaultHome=bool(bp.get("isDefaultHome", False)),
                header=header_cfg,
                items=items,
            )
        )
        capacity_report.append({
            "pageId": page_id,
            "title": page_title,
            "itemsPlaced": interactive_count,
            "maxOneHandSlots": 8,
            "remainingSlots": max(0, 8 - interactive_count),
            "overflowCount": 0,
        })

    # Final page: clean Wallpaper page
    wp_items: list[PageItemSpec] = []
    if not empty_page_retained:
        wp_items.append(
            PageItemSpec(
                id="wallpaper:placeholder",
                type="placeholder_widget",
                title="Wallpaper Spacer",
                provider="com.homestudio.local/.widget.WallpaperPlaceholderWidgetProvider",
                position=PositionSpec(row=0, column=0, rowSpan=1, columnSpan=1),
            )
        )
    pages.append(
        PageSpec(
            id="wallpaper",
            title=None,
            order=len(pages),
            isDefaultHome=False,
            retainEmptyPage=True,
            header=HeaderInstanceConfig(enabled=False),
            items=wp_items,
        )
    )
    capacity_report.append({
        "pageId": "wallpaper",
        "title": "Wallpaper (Clean)",
        "itemsPlaced": 0,
        "maxOneHandSlots": 0,
        "remainingSlots": 0,
        "overflowCount": 0,
    })

    layout = DesiredLayout(
        schemaVersion=1,
        launcher=LauncherConfig(
            type="samsung-one-ui",
            preserveExistingWallpaper=True,
            grid=GridSpec(columns=columns, rows=rows),
        ),
        theme=ThemeConfig(
            header=HeaderTheme(
                fontFamily="sans-serif",
                fontWeight=300,
                fontSizeSp=26.0,
                textColor="#FFFFFFFF",
                opacity=0.92,
                alignment="start",
                backgroundColor="#00000000",
                paddingDp=8,
                letterSpacing=0.03,
                showSeparator=False,
            )
        ),
        wallpaper=WallpaperConfig(
            mode="preserve",
            source="assets/wallpaper/reconstructed_wallpaper.png",
            type="samsung-layered-live",
        ),
        dock=DockConfig(mode="preserve", items=[]),
        pages=pages,
    )
    return layout, {"pages": capacity_report, "totalPages": len(pages)}
