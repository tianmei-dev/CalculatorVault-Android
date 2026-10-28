.class public final Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;
.super Landroid/app/Service;
.source "AppLockMonitorService.kt"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$Companion;
    }
.end annotation

.annotation system Ldalvik/annotation/SourceDebugExtension;
    value = "SMAP\nAppLockMonitorService.kt\nKotlin\n*S Kotlin\n*F\n+ 1 AppLockMonitorService.kt\ncom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,317:1\n1#2:318\n*E\n"
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000p\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0008\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0008\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\"\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0008\n\u0000\n\u0002\u0018\u0002\n\u0002\u0008\u0005\n\u0002\u0018\u0002\n\u0002\u0008\u0004\n\u0002\u0018\u0002\n\u0002\u0008\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0008\u000b\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0018\u0002\n\u0000\u0008\u0007\u0018\u0000 82\u00020\u0001:\u00018B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0008\u0010\u0011\u001a\u00020\u0012H\u0016J\"\u0010\u0013\u001a\u00020\u00142\u0008\u0010\u0015\u001a\u0004\u0018\u00010\u00162\u0006\u0010\u0017\u001a\u00020\u00142\u0006\u0010\u0018\u001a\u00020\u0014H\u0016J\u0008\u0010\u0019\u001a\u00020\u0012H\u0016J\u0008\u0010\u001a\u001a\u00020\u0012H\u0002J\u0014\u0010\u001b\u001a\u0004\u0018\u00010\u001c2\u0008\u0010\u0015\u001a\u0004\u0018\u00010\u0016H\u0016J\u0008\u0010\u001d\u001a\u00020\u0012H\u0002J\u0008\u0010\u001e\u001a\u00020\u0012H\u0002J$\u0010\u001f\u001a\u00020\u00122\u0006\u0010 \u001a\u00020!2\u000c\u0010\"\u001a\u0008\u0012\u0004\u0012\u00020\u00100\u000fH\u0082@\u00a2\u0006\u0002\u0010#J&\u0010$\u001a\u00020\u00122\u0006\u0010 \u001a\u00020!2\u0006\u0010%\u001a\u00020&2\u0006\u0010\'\u001a\u00020(H\u0082@\u00a2\u0006\u0002\u0010)J\u0018\u0010*\u001a\u00020\u00122\u0006\u0010 \u001a\u00020!2\u0006\u0010+\u001a\u00020\u0010H\u0002J\u0018\u0010,\u001a\u00020\u00122\u0006\u0010 \u001a\u00020!2\u0006\u0010+\u001a\u00020\u0010H\u0002J\u0018\u0010-\u001a\u00020\u00122\u0006\u0010 \u001a\u00020!2\u0006\u0010.\u001a\u00020\u0010H\u0002J\u0008\u00102\u001a\u00020\u0012H\u0002J\u0008\u00103\u001a\u000204H\u0002J\u0008\u00105\u001a\u00020\u0012H\u0002J\u0008\u00106\u001a\u000207H\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0008\u001a\u0004\u0018\u00010\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\n\u001a\u0004\u0018\u00010\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000c\u001a\u0004\u0018\u00010\rX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000e\u001a\u0008\u0012\u0004\u0012\u00020\u00100\u000fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0014\u0010/\u001a\u00020\u00108BX\u0082\u0004\u00a2\u0006\u0006\u001a\u0004\u00080\u00101\u00a8\u0006 "
    }
    d2 = {
        "Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;",
        "Landroid/app/Service;",
        "<init>",
        "()V",
        "serviceJob",
        "Lkotlinx/coroutines/CompletableJob;",
        "serviceScope",
        "Lkotlinx/coroutines/CoroutineScope;",
        "monitorJob",
        "Lkotlinx/coroutines/Job;",
        "lockedPackagesJob",
        "clearUnlockJob",
        "overlayController",
        "Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;",
        "lockedPackages",
        "",
        "",
        "onCreate",
        "",
        "onStartCommand",
        "",
        "intent",
        "Landroid/content/Intent;",
        "flags",
        "startId",
        "onDestroy",
        "releaseMonitoring",
        "onBind",
        "Landroid/os/IBinder;",
        "startMonitoring",
        "observeLockedPackages",
        "handleLockedPackagesChanged",
        "app",
        "Lcom/aurora/calculatorvault/app/CalculatorVaultApp;",
        "packages",
        "(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/util/Set;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
        "handleForegroundResult",
        "overlayPermissionHelper",
        "Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;",
        "result",
        "Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;",
        "(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
        "handleTemporaryUnlockLifecycle",
        "foregroundPackage",
        "handleVerificationLifecycle",
        "scheduleClear",
        "packageName",
        "packageNameSelf",
        "getPackageNameSelf",
        "()Ljava/lang/String;",
        "startAsForegroundIfPossible",
        "buildNotification",
        "Landroid/app/Notification;",
        "createNotificationChannel",
        "notificationManager",
        "Landroid/app/NotificationManager;",
        "Companion"
    }
    k = 0x1
    mv = {
        0x2,
        0x2,
        0x0
    }
    xi = 0x30
.end annotation


# static fields
.field public static final $stable:I

.field private static final ACTION_STOP:Ljava/lang/String; = "com.aurora.calculatorvault.action.STOP_APP_LOCK_MONITOR"

.field public static final CHANNEL_ID:Ljava/lang/String; = "app_lock_monitor"

.field public static final Companion:Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$Companion;

.field private static final NOTIFICATION_ID:I = 0x1005

.field private static final TAG:Ljava/lang/String; = "CV_APPLOCK"

.field private static volatile isRunning:Z

.field private static runningService:Ljava/lang/ref/WeakReference;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/lang/ref/WeakReference<",
            "Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;",
            ">;"
        }
    .end annotation
.end field


# instance fields
.field private clearUnlockJob:Lkotlinx/coroutines/Job;

.field private volatile lockedPackages:Ljava/util/Set;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;"
        }
    .end annotation
.end field

.field private lockedPackagesJob:Lkotlinx/coroutines/Job;

.field private monitorJob:Lkotlinx/coroutines/Job;

.field private overlayController:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;

.field private final serviceJob:Lkotlinx/coroutines/CompletableJob;

.field private final serviceScope:Lkotlinx/coroutines/CoroutineScope;


# direct methods
.method static constructor <clinit>()V
    .registers 2

    new-instance v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$Companion;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$Companion;-><init>(Lkotlin/jvm/internal/DefaultConstructorMarker;)V

    sput-object v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->Companion:Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$Companion;

    const/16 v0, 0x8

    sput v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->$stable:I

    return-void
.end method

.method public constructor <init>()V
    .registers 3

    .line 32
    invoke-direct {p0}, Landroid/app/Service;-><init>()V

    const/4 v0, 0x0

    const/4 v1, 0x1

    .line 33
    invoke-static {v0, v1, v0}, Lkotlinx/coroutines/SupervisorKt;->SupervisorJob$default(Lkotlinx/coroutines/Job;ILjava/lang/Object;)Lkotlinx/coroutines/CompletableJob;

    move-result-object v0

    iput-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->serviceJob:Lkotlinx/coroutines/CompletableJob;

    .line 34
    invoke-static {}, Lkotlinx/coroutines/Dispatchers;->getDefault()Lkotlinx/coroutines/CoroutineDispatcher;

    move-result-object v1

    check-cast v1, Lkotlin/coroutines/CoroutineContext;

    invoke-interface {v0, v1}, Lkotlinx/coroutines/CompletableJob;->plus(Lkotlin/coroutines/CoroutineContext;)Lkotlin/coroutines/CoroutineContext;

    move-result-object v0

    invoke-static {v0}, Lkotlinx/coroutines/CoroutineScopeKt;->CoroutineScope(Lkotlin/coroutines/CoroutineContext;)Lkotlinx/coroutines/CoroutineScope;

    move-result-object v0

    iput-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->serviceScope:Lkotlinx/coroutines/CoroutineScope;

    .line 41
    invoke-static {}, Lkotlin/collections/SetsKt;->emptySet()Ljava/util/Set;

    move-result-object v0

    iput-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->lockedPackages:Ljava/util/Set;

    return-void
.end method

.method public static final synthetic access$getOverlayController$p(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;)Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;
    .registers 1

    .line 32
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->overlayController:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;

    return-object p0
.end method

.method public static final synthetic access$getRunningService$cp()Ljava/lang/ref/WeakReference;
    .registers 1

    .line 32
    sget-object v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->runningService:Ljava/lang/ref/WeakReference;

    return-object v0
.end method

.method public static final synthetic access$handleForegroundResult(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
    .registers 5

    .line 32
    invoke-direct {p0, p1, p2, p3, p4}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->handleForegroundResult(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object p0

    return-object p0
.end method

.method public static final synthetic access$handleLockedPackagesChanged(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/util/Set;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
    .registers 4

    .line 32
    invoke-direct {p0, p1, p2, p3}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->handleLockedPackagesChanged(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/util/Set;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object p0

    return-object p0
.end method

.method public static final synthetic access$isRunning$cp()Z
    .registers 1

    .line 32
    sget-boolean v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->isRunning:Z

    return v0
.end method

.method public static final synthetic access$observeLockedPackages(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;)V
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->observeLockedPackages()V

    return-void
.end method

.method public static final synthetic access$releaseMonitoring(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;)V
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->releaseMonitoring()V

    return-void
.end method

.method public static final synthetic access$setLockedPackages$p(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Ljava/util/Set;)V
    .registers 2

    .line 32
    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->lockedPackages:Ljava/util/Set;

    return-void
.end method

.method public static final synthetic access$startAsForegroundIfPossible(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;)V
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->startAsForegroundIfPossible()V

    return-void
.end method

.method public static final synthetic access$startMonitoring(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;)V
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->startMonitoring()V

    return-void
.end method

.method private final buildNotification()Landroid/app/Notification;
    .registers 4

    .line 262
    new-instance v0, Landroidx/core/app/NotificationCompat$Builder;

    move-object v1, p0

    check-cast v1, Landroid/content/Context;

    const-string v2, "app_lock_monitor"

    invoke-direct {v0, v1, v2}, Landroidx/core/app/NotificationCompat$Builder;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    .line 263
    sget v1, Lcom/aurora/calculatorvault/R$drawable;->ic_launcher:I

    invoke-virtual {v0, v1}, Landroidx/core/app/NotificationCompat$Builder;->setSmallIcon(I)Landroidx/core/app/NotificationCompat$Builder;

    move-result-object v0

    .line 264
    sget v1, Lcom/aurora/calculatorvault/R$string;->app_lock_monitor_notification_title:I

    invoke-virtual {p0, v1}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getString(I)Ljava/lang/String;

    move-result-object v1

    check-cast v1, Ljava/lang/CharSequence;

    invoke-virtual {v0, v1}, Landroidx/core/app/NotificationCompat$Builder;->setContentTitle(Ljava/lang/CharSequence;)Landroidx/core/app/NotificationCompat$Builder;

    move-result-object v0

    .line 265
    sget v1, Lcom/aurora/calculatorvault/R$string;->app_lock_monitor_notification_text:I

    invoke-virtual {p0, v1}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getString(I)Ljava/lang/String;

    move-result-object p0

    check-cast p0, Ljava/lang/CharSequence;

    invoke-virtual {v0, p0}, Landroidx/core/app/NotificationCompat$Builder;->setContentText(Ljava/lang/CharSequence;)Landroidx/core/app/NotificationCompat$Builder;

    move-result-object p0

    const/4 v0, 0x1

    .line 266
    invoke-virtual {p0, v0}, Landroidx/core/app/NotificationCompat$Builder;->setOngoing(Z)Landroidx/core/app/NotificationCompat$Builder;

    move-result-object p0

    .line 267
    invoke-virtual {p0, v0}, Landroidx/core/app/NotificationCompat$Builder;->setSilent(Z)Landroidx/core/app/NotificationCompat$Builder;

    move-result-object p0

    .line 268
    invoke-virtual {p0}, Landroidx/core/app/NotificationCompat$Builder;->build()Landroid/app/Notification;

    move-result-object p0

    const-string v0, "build(...)"

    invoke-static {p0, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    return-object p0
.end method

.method private final createNotificationChannel()V
    .registers 5

    .line 272
    new-instance v0, Landroid/app/NotificationChannel;

    .line 274
    sget v1, Lcom/aurora/calculatorvault/R$string;->app_lock_monitor_channel:I

    invoke-virtual {p0, v1}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getString(I)Ljava/lang/String;

    move-result-object v1

    check-cast v1, Ljava/lang/CharSequence;

    const/4 v2, 0x2

    .line 272
    const-string v3, "app_lock_monitor"

    invoke-direct {v0, v3, v1, v2}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V

    .line 277
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->notificationManager()Landroid/app/NotificationManager;

    move-result-object p0

    invoke-virtual {p0, v0}, Landroid/app/NotificationManager;->createNotificationChannel(Landroid/app/NotificationChannel;)V

    return-void
.end method

.method private final getPackageNameSelf()Ljava/lang/String;
    .registers 2

    .line 250
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getPackageName()Ljava/lang/String;

    move-result-object p0

    const-string v0, "getPackageName(...)"

    invoke-static {p0, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    return-object p0
.end method

.method private final handleForegroundResult(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
    .registers 21
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/aurora/calculatorvault/app/CalculatorVaultApp;",
            "Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;",
            "Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;",
            "Lkotlin/coroutines/Continuation<",
            "-",
            "Lkotlin/Unit;",
            ">;)",
            "Ljava/lang/Object;"
        }
    .end annotation

    move-object/from16 v2, p0

    move-object/from16 v1, p1

    move-object/from16 v3, p3

    move-object/from16 v0, p4

    instance-of v4, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;

    if-eqz v4, :cond_1c

    move-object v4, v0

    check-cast v4, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;

    iget v5, v4, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->label:I

    const/high16 v6, -0x80000000

    and-int/2addr v5, v6

    if-eqz v5, :cond_1c

    iget v0, v4, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->label:I

    sub-int/2addr v0, v6

    iput v0, v4, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->label:I

    goto :goto_21

    :cond_1c
    new-instance v4, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;

    invoke-direct {v4, v2, v0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;-><init>(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lkotlin/coroutines/Continuation;)V

    :goto_21
    move-object v8, v4

    iget-object v0, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->result:Ljava/lang/Object;

    invoke-static {}, Lkotlin/coroutines/intrinsics/IntrinsicsKt;->getCOROUTINE_SUSPENDED()Ljava/lang/Object;

    move-result-object v9

    .line 162
    iget v4, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->label:I

    const/4 v10, 0x3

    const/4 v5, 0x2

    const/4 v6, 0x1

    const/4 v7, 0x0

    if-eqz v4, :cond_a6

    if-eq v4, v6, :cond_8b

    if-eq v4, v5, :cond_5d

    if-ne v4, v10, :cond_55

    iget-wide v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->J$0:J

    iget-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$5:Ljava/lang/Object;

    check-cast v1, Ljava/lang/String;

    iget-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$4:Ljava/lang/Object;

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    iget-object v2, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$3:Ljava/lang/Object;

    check-cast v2, Ljava/lang/String;

    iget-object v3, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$2:Ljava/lang/Object;

    check-cast v3, Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;

    iget-object v3, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$1:Ljava/lang/Object;

    check-cast v3, Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;

    iget-object v3, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$0:Ljava/lang/Object;

    check-cast v3, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    invoke-static {v0}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V

    goto/16 :goto_1d9

    :cond_55
    new-instance v0, Ljava/lang/IllegalStateException;

    const-string v1, "call to \'resume\' before \'invoke\' with coroutine"

    invoke-direct {v0, v1}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw v0

    :cond_5d
    iget v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->I$0:I

    iget-wide v3, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->J$0:J

    iget-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$5:Ljava/lang/Object;

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;

    iget-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$4:Ljava/lang/Object;

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    iget-object v5, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$3:Ljava/lang/Object;

    check-cast v5, Ljava/lang/String;

    iget-object v6, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$2:Ljava/lang/Object;

    check-cast v6, Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;

    iget-object v11, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$1:Ljava/lang/Object;

    check-cast v11, Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;

    iget-object v12, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$0:Ljava/lang/Object;

    check-cast v12, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    :try_start_79
    invoke-static {v0}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V
    :try_end_7c
    .catchall {:try_start_79 .. :try_end_7c} :catchall_82

    move-object v15, v6

    move-object v6, v5

    move-wide v4, v3

    move-object v3, v15

    goto/16 :goto_173

    :catchall_82
    move-exception v0

    move-object v13, v6

    move-object v6, v5

    move-wide v4, v3

    move-object v3, v13

    :goto_87
    move-object v13, v1

    move-object v1, v12

    goto/16 :goto_188

    :cond_8b
    iget-wide v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->J$0:J

    iget-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$4:Ljava/lang/Object;

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    iget-object v2, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$3:Ljava/lang/Object;

    check-cast v2, Ljava/lang/String;

    iget-object v3, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$2:Ljava/lang/Object;

    check-cast v3, Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult;

    iget-object v3, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$1:Ljava/lang/Object;

    check-cast v3, Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;

    iget-object v3, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$0:Ljava/lang/Object;

    check-cast v3, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    invoke-static {v0}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V

    goto/16 :goto_137

    :cond_a6
    invoke-static {v0}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V

    .line 167
    invoke-virtual {v1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getPrivacyWithdrawn()Z

    move-result v0

    if-eqz v0, :cond_b2

    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    .line 168
    :cond_b2
    instance-of v0, v3, Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult$Success;

    if-eqz v0, :cond_ba

    move-object v0, v3

    check-cast v0, Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult$Success;

    goto :goto_bb

    :cond_ba
    move-object v0, v7

    :goto_bb
    if-eqz v0, :cond_1e7

    invoke-virtual {v0}, Lcom/aurora/calculatorvault/feature/applock/domain/ForegroundAppResult$Success;->getPackageName()Ljava/lang/String;

    move-result-object v4

    if-nez v4, :cond_c5

    goto/16 :goto_1e7

    .line 169
    :cond_c5
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J

    move-result-wide v11

    .line 170
    invoke-virtual {v1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object v13

    .line 171
    invoke-virtual {v13, v4, v11, v12}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->updateForegroundPackage(Ljava/lang/String;J)Z

    .line 175
    invoke-direct {v2, v1, v4}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->handleTemporaryUnlockLifecycle(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/lang/String;)V

    .line 176
    invoke-direct {v2, v1, v4}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->handleVerificationLifecycle(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/lang/String;)V

    .line 178
    invoke-direct {v2}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getPackageNameSelf()Ljava/lang/String;

    move-result-object v0

    invoke-static {v4, v0}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_e3

    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    .line 179
    :cond_e3
    iget-object v0, v2, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->lockedPackages:Ljava/util/Set;

    invoke-interface {v0, v4}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_ee

    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    .line 181
    :cond_ee
    invoke-virtual {v13, v4}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->isTemporarilyUnlocked(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_f7

    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    .line 182
    :cond_f7
    invoke-virtual {v13, v4}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->beginVerification(Ljava/lang/String;)Z

    move-result v0

    if-nez v0, :cond_100

    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    .line 185
    :cond_100
    invoke-virtual/range {p2 .. p2}, Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;->isGranted()Z

    move-result v0

    if-nez v0, :cond_13d

    .line 187
    invoke-static {}, Lkotlinx/coroutines/Dispatchers;->getMain()Lkotlinx/coroutines/MainCoroutineDispatcher;

    move-result-object v0

    check-cast v0, Lkotlin/coroutines/CoroutineContext;

    new-instance v5, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$2;

    invoke-direct {v5, v2, v7}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$2;-><init>(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lkotlin/coroutines/Continuation;)V

    check-cast v5, Lkotlin/jvm/functions/Function2;

    invoke-static {v1}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    iput-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$0:Ljava/lang/Object;

    invoke-static/range {p2 .. p2}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    iput-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$1:Ljava/lang/Object;

    invoke-static {v3}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    iput-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$2:Ljava/lang/Object;

    iput-object v4, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$3:Ljava/lang/Object;

    iput-object v13, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$4:Ljava/lang/Object;

    iput-wide v11, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->J$0:J

    iput v6, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->label:I

    invoke-static {v0, v5, v8}, Lkotlinx/coroutines/BuildersKt;->withContext(Lkotlin/coroutines/CoroutineContext;Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object v0

    if-ne v0, v9, :cond_135

    goto/16 :goto_1d6

    :cond_135
    move-object v2, v4

    move-object v1, v13

    .line 188
    :goto_137
    invoke-virtual {v1, v2}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->finishVerification(Ljava/lang/String;)V

    .line 189
    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    .line 192
    :cond_13d
    :try_start_13d
    sget-object v0, Lkotlin/Result;->Companion:Lkotlin/Result$Companion;

    move-object v0, v2

    check-cast v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;

    invoke-virtual {v1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getHiddenAppRuntime()Lcom/aurora/calculatorvault/feature/hiddenapp/domain/AndroidHiddenAppRuntime;

    move-result-object v0

    iput-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$0:Ljava/lang/Object;

    invoke-static/range {p2 .. p2}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v6

    iput-object v6, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$1:Ljava/lang/Object;

    invoke-static {v3}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v6

    iput-object v6, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$2:Ljava/lang/Object;

    iput-object v4, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$3:Ljava/lang/Object;

    iput-object v13, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$4:Ljava/lang/Object;

    invoke-static {v2}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v6

    iput-object v6, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$5:Ljava/lang/Object;

    iput-wide v11, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->J$0:J

    const/4 v6, 0x0

    iput v6, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->I$0:I

    iput v5, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->label:I

    invoke-virtual {v0, v4, v8}, Lcom/aurora/calculatorvault/feature/hiddenapp/domain/AndroidHiddenAppRuntime;->resolve(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object v0
    :try_end_169
    .catchall {:try_start_13d .. :try_end_169} :catchall_183

    if-ne v0, v9, :cond_16d

    goto/16 :goto_1d6

    :cond_16d
    move-object v6, v4

    move-wide v4, v11

    move-object/from16 v11, p2

    move-object v12, v1

    move-object v1, v13

    :goto_173
    :try_start_173
    check-cast v0, Lcom/aurora/calculatorvault/feature/hiddenapp/domain/InstalledAppRuntimeInfo;

    invoke-virtual {v0}, Lcom/aurora/calculatorvault/feature/hiddenapp/domain/InstalledAppRuntimeInfo;->getAppName()Ljava/lang/String;

    move-result-object v0

    invoke-static {v0}, Lkotlin/Result;->constructor-impl(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0
    :try_end_17d
    .catchall {:try_start_173 .. :try_end_17d} :catchall_180

    move-object v13, v1

    move-object v1, v12

    goto :goto_192

    :catchall_180
    move-exception v0

    goto/16 :goto_87

    :catchall_183
    move-exception v0

    move-object v6, v4

    move-wide v4, v11

    move-object/from16 v11, p2

    :goto_188
    sget-object v12, Lkotlin/Result;->Companion:Lkotlin/Result$Companion;

    invoke-static {v0}, Lkotlin/ResultKt;->createFailure(Ljava/lang/Throwable;)Ljava/lang/Object;

    move-result-object v0

    invoke-static {v0}, Lkotlin/Result;->constructor-impl(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    :goto_192
    move-object v12, v11

    move-object v11, v3

    move-object v3, v6

    move-wide v5, v4

    invoke-static {v0}, Lkotlin/Result;->isFailure-impl(Ljava/lang/Object;)Z

    move-result v4

    if-eqz v4, :cond_19d

    goto :goto_19e

    :cond_19d
    move-object v7, v0

    :goto_19e
    move-object v4, v7

    check-cast v4, Ljava/lang/String;

    .line 193
    invoke-static {}, Lkotlinx/coroutines/Dispatchers;->getMain()Lkotlinx/coroutines/MainCoroutineDispatcher;

    move-result-object v0

    move-object v14, v0

    check-cast v14, Lkotlin/coroutines/CoroutineContext;

    new-instance v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$shown$1;

    const/4 v7, 0x0

    invoke-direct/range {v0 .. v7}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$shown$1;-><init>(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Ljava/lang/String;Ljava/lang/String;JLkotlin/coroutines/Continuation;)V

    check-cast v0, Lkotlin/jvm/functions/Function2;

    invoke-static {v1}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    iput-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$0:Ljava/lang/Object;

    invoke-static {v12}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    iput-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$1:Ljava/lang/Object;

    invoke-static {v11}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    iput-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$2:Ljava/lang/Object;

    iput-object v3, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$3:Ljava/lang/Object;

    iput-object v13, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$4:Ljava/lang/Object;

    invoke-static {v4}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v1

    iput-object v1, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->L$5:Ljava/lang/Object;

    iput-wide v5, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->J$0:J

    iput v10, v8, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleForegroundResult$1;->label:I

    invoke-static {v14, v0, v8}, Lkotlinx/coroutines/BuildersKt;->withContext(Lkotlin/coroutines/CoroutineContext;Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object v0

    if-ne v0, v9, :cond_1d7

    :goto_1d6
    return-object v9

    :cond_1d7
    move-object v2, v3

    move-object v1, v13

    :goto_1d9
    check-cast v0, Ljava/lang/Boolean;

    invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z

    move-result v0

    if-nez v0, :cond_1e4

    .line 201
    invoke-virtual {v1, v2}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->finishVerification(Ljava/lang/String;)V

    .line 203
    :cond_1e4
    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    .line 168
    :cond_1e7
    :goto_1e7
    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0
.end method

.method private final handleLockedPackagesChanged(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/util/Set;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
    .registers 13
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lcom/aurora/calculatorvault/app/CalculatorVaultApp;",
            "Ljava/util/Set<",
            "Ljava/lang/String;",
            ">;",
            "Lkotlin/coroutines/Continuation<",
            "-",
            "Lkotlin/Unit;",
            ">;)",
            "Ljava/lang/Object;"
        }
    .end annotation

    instance-of v0, p3, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;

    if-eqz v0, :cond_14

    move-object v0, p3

    check-cast v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;

    iget v1, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->label:I

    const/high16 v2, -0x80000000

    and-int/2addr v1, v2

    if-eqz v1, :cond_14

    iget p3, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->label:I

    sub-int/2addr p3, v2

    iput p3, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->label:I

    goto :goto_19

    :cond_14
    new-instance v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;

    invoke-direct {v0, p0, p3}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;-><init>(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lkotlin/coroutines/Continuation;)V

    :goto_19
    iget-object p3, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->result:Ljava/lang/Object;

    invoke-static {}, Lkotlin/coroutines/intrinsics/IntrinsicsKt;->getCOROUTINE_SUSPENDED()Ljava/lang/Object;

    move-result-object v1

    .line 139
    iget v2, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->label:I

    const/4 v3, 0x0

    const/4 v4, 0x2

    const/4 v5, 0x1

    if-eqz v2, :cond_5d

    if-eq v2, v5, :cond_47

    if-ne v2, v4, :cond_3f

    iget-object p1, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$3:Ljava/lang/Object;

    check-cast p1, Ljava/lang/String;

    iget-object p1, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$2:Ljava/lang/Object;

    check-cast p1, Ljava/lang/String;

    iget-object p1, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$1:Ljava/lang/Object;

    check-cast p1, Ljava/util/Set;

    iget-object p1, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$0:Ljava/lang/Object;

    check-cast p1, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    invoke-static {p3}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V

    goto/16 :goto_e4

    :cond_3f
    new-instance p0, Ljava/lang/IllegalStateException;

    const-string p1, "call to \'resume\' before \'invoke\' with coroutine"

    invoke-direct {p0, p1}, Ljava/lang/IllegalStateException;-><init>(Ljava/lang/String;)V

    throw p0

    :cond_47
    iget-object p1, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$3:Ljava/lang/Object;

    check-cast p1, Ljava/lang/String;

    iget-object p2, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$2:Ljava/lang/Object;

    check-cast p2, Ljava/lang/String;

    iget-object v2, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$1:Ljava/lang/Object;

    check-cast v2, Ljava/util/Set;

    iget-object v5, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$0:Ljava/lang/Object;

    check-cast v5, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    invoke-static {p3}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V

    move-object p3, p2

    move-object p2, v2

    goto :goto_a3

    :cond_5d
    invoke-static {p3}, Lkotlin/ResultKt;->throwOnFailure(Ljava/lang/Object;)V

    .line 143
    invoke-virtual {p1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object p3

    invoke-virtual {p3}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->currentVerifyingPackage()Ljava/lang/String;

    move-result-object p3

    .line 144
    invoke-virtual {p1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object v2

    invoke-virtual {v2}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->currentUnlockedPackage()Ljava/lang/String;

    move-result-object v2

    if-eqz p3, :cond_a5

    .line 145
    invoke-interface {p2, p3}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v6

    if-nez v6, :cond_a5

    .line 146
    invoke-virtual {p1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object v6

    invoke-virtual {v6, p3}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->finishVerification(Ljava/lang/String;)V

    .line 147
    invoke-static {}, Lkotlinx/coroutines/Dispatchers;->getMain()Lkotlinx/coroutines/MainCoroutineDispatcher;

    move-result-object v6

    check-cast v6, Lkotlin/coroutines/CoroutineContext;

    new-instance v7, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$2;

    invoke-direct {v7, p0, v3}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$2;-><init>(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lkotlin/coroutines/Continuation;)V

    check-cast v7, Lkotlin/jvm/functions/Function2;

    iput-object p1, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$0:Ljava/lang/Object;

    iput-object p2, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$1:Ljava/lang/Object;

    invoke-static {p3}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v8

    iput-object v8, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$2:Ljava/lang/Object;

    iput-object v2, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$3:Ljava/lang/Object;

    iput v5, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->label:I

    invoke-static {v6, v7, v0}, Lkotlinx/coroutines/BuildersKt;->withContext(Lkotlin/coroutines/CoroutineContext;Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object v5

    if-ne v5, v1, :cond_a1

    goto :goto_e3

    :cond_a1
    move-object v5, p1

    move-object p1, v2

    :goto_a3
    move-object v2, p1

    move-object p1, v5

    :cond_a5
    if-eqz v2, :cond_b4

    .line 150
    invoke-interface {p2, v2}, Ljava/util/Set;->contains(Ljava/lang/Object;)Z

    move-result v5

    if-nez v5, :cond_b4

    .line 151
    invoke-virtual {p1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object v5

    invoke-virtual {v5, v2}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->clearUnlocked(Ljava/lang/String;)V

    .line 154
    :cond_b4
    invoke-interface {p2}, Ljava/util/Set;->isEmpty()Z

    move-result v5

    if-eqz v5, :cond_ee

    .line 155
    invoke-static {}, Lkotlinx/coroutines/Dispatchers;->getMain()Lkotlinx/coroutines/MainCoroutineDispatcher;

    move-result-object v5

    check-cast v5, Lkotlin/coroutines/CoroutineContext;

    new-instance v6, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$3;

    invoke-direct {v6, p0, v3}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$3;-><init>(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lkotlin/coroutines/Continuation;)V

    check-cast v6, Lkotlin/jvm/functions/Function2;

    iput-object p1, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$0:Ljava/lang/Object;

    invoke-static {p2}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p2

    iput-object p2, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$1:Ljava/lang/Object;

    invoke-static {p3}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p2

    iput-object p2, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$2:Ljava/lang/Object;

    invoke-static {v2}, Lkotlin/coroutines/jvm/internal/SpillingKt;->nullOutSpilledVariable(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p2

    iput-object p2, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->L$3:Ljava/lang/Object;

    iput v4, v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$handleLockedPackagesChanged$1;->label:I

    invoke-static {v5, v6, v0}, Lkotlinx/coroutines/BuildersKt;->withContext(Lkotlin/coroutines/CoroutineContext;Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object p2

    if-ne p2, v1, :cond_e4

    :goto_e3
    return-object v1

    .line 156
    :cond_e4
    :goto_e4
    invoke-virtual {p1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object p1

    invoke-virtual {p1}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->clearAll()V

    .line 158
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->stopSelf()V

    .line 160
    :cond_ee
    sget-object p0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object p0
.end method

.method private final handleTemporaryUnlockLifecycle(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/lang/String;)V
    .registers 4

    .line 209
    invoke-virtual {p1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object v0

    invoke-virtual {v0}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->currentUnlockedPackage()Ljava/lang/String;

    move-result-object v0

    if-nez v0, :cond_b

    return-void

    .line 210
    :cond_b
    invoke-static {p2, v0}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_1d

    .line 211
    iget-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->clearUnlockJob:Lkotlinx/coroutines/Job;

    const/4 p2, 0x0

    if-eqz p1, :cond_1a

    const/4 v0, 0x1

    invoke-static {p1, p2, v0, p2}, Lkotlinx/coroutines/Job$DefaultImpls;->cancel$default(Lkotlinx/coroutines/Job;Ljava/util/concurrent/CancellationException;ILjava/lang/Object;)V

    .line 212
    :cond_1a
    iput-object p2, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->clearUnlockJob:Lkotlinx/coroutines/Job;

    return-void

    .line 216
    :cond_1d
    invoke-direct {p0, p1, v0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->scheduleClear(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/lang/String;)V

    return-void
.end method

.method private final handleVerificationLifecycle(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/lang/String;)V
    .registers 4

    .line 223
    invoke-virtual {p1}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object v0

    invoke-virtual {v0}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->currentVerifyingPackage()Ljava/lang/String;

    move-result-object v0

    if-nez v0, :cond_b

    return-void

    .line 224
    :cond_b
    invoke-static {p2, v0}, Lkotlin/jvm/internal/Intrinsics;->areEqual(Ljava/lang/Object;Ljava/lang/Object;)Z

    move-result p2

    if-eqz p2, :cond_1d

    .line 225
    iget-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->clearUnlockJob:Lkotlinx/coroutines/Job;

    const/4 p2, 0x0

    if-eqz p1, :cond_1a

    const/4 v0, 0x1

    invoke-static {p1, p2, v0, p2}, Lkotlinx/coroutines/Job$DefaultImpls;->cancel$default(Lkotlinx/coroutines/Job;Ljava/util/concurrent/CancellationException;ILjava/lang/Object;)V

    .line 226
    :cond_1a
    iput-object p2, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->clearUnlockJob:Lkotlinx/coroutines/Job;

    return-void

    .line 229
    :cond_1d
    invoke-direct {p0, p1, v0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->scheduleClear(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/lang/String;)V

    return-void
.end method

.method private final notificationManager()Landroid/app/NotificationManager;
    .registers 2

    .line 281
    const-class v0, Landroid/app/NotificationManager;

    invoke-virtual {p0, v0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object p0

    const-string v0, "getSystemService(...)"

    invoke-static {p0, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast p0, Landroid/app/NotificationManager;

    return-object p0
.end method

.method private final observeLockedPackages()V
    .registers 8

    .line 128
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->lockedPackagesJob:Lkotlinx/coroutines/Job;

    if-eqz v0, :cond_c

    invoke-interface {v0}, Lkotlinx/coroutines/Job;->isActive()Z

    move-result v0

    const/4 v1, 0x1

    if-ne v0, v1, :cond_c

    return-void

    .line 129
    :cond_c
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    const-string v1, "null cannot be cast to non-null type com.aurora.calculatorvault.app.CalculatorVaultApp"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNull(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast v0, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    .line 130
    iget-object v1, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->serviceScope:Lkotlinx/coroutines/CoroutineScope;

    new-instance v2, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$observeLockedPackages$1;

    const/4 v3, 0x0

    invoke-direct {v2, v0, p0, v3}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$observeLockedPackages$1;-><init>(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lkotlin/coroutines/Continuation;)V

    move-object v4, v2

    check-cast v4, Lkotlin/jvm/functions/Function2;

    const/4 v5, 0x3

    const/4 v6, 0x0

    const/4 v2, 0x0

    invoke-static/range {v1 .. v6}, Lkotlinx/coroutines/BuildersKt;->launch$default(Lkotlinx/coroutines/CoroutineScope;Lkotlin/coroutines/CoroutineContext;Lkotlinx/coroutines/CoroutineStart;Lkotlin/jvm/functions/Function2;ILjava/lang/Object;)Lkotlinx/coroutines/Job;

    move-result-object v0

    iput-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->lockedPackagesJob:Lkotlinx/coroutines/Job;

    return-void
.end method

.method private final releaseMonitoring()V
    .registers 5

    .line 88
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->monitorJob:Lkotlinx/coroutines/Job;

    const/4 v1, 0x1

    const/4 v2, 0x0

    if-eqz v0, :cond_9

    invoke-static {v0, v2, v1, v2}, Lkotlinx/coroutines/Job$DefaultImpls;->cancel$default(Lkotlinx/coroutines/Job;Ljava/util/concurrent/CancellationException;ILjava/lang/Object;)V

    .line 89
    :cond_9
    iput-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->monitorJob:Lkotlinx/coroutines/Job;

    .line 90
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->lockedPackagesJob:Lkotlinx/coroutines/Job;

    if-eqz v0, :cond_12

    invoke-static {v0, v2, v1, v2}, Lkotlinx/coroutines/Job$DefaultImpls;->cancel$default(Lkotlinx/coroutines/Job;Ljava/util/concurrent/CancellationException;ILjava/lang/Object;)V

    .line 91
    :cond_12
    iput-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->lockedPackagesJob:Lkotlinx/coroutines/Job;

    .line 92
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->clearUnlockJob:Lkotlinx/coroutines/Job;

    if-eqz v0, :cond_1b

    invoke-static {v0, v2, v1, v2}, Lkotlinx/coroutines/Job$DefaultImpls;->cancel$default(Lkotlinx/coroutines/Job;Ljava/util/concurrent/CancellationException;ILjava/lang/Object;)V

    .line 93
    :cond_1b
    iput-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->clearUnlockJob:Lkotlinx/coroutines/Job;

    .line 94
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->overlayController:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;

    if-eqz v0, :cond_24

    invoke-virtual {v0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->hide()V

    .line 95
    :cond_24
    iput-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->overlayController:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;

    .line 96
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    const-string v3, "null cannot be cast to non-null type com.aurora.calculatorvault.app.CalculatorVaultApp"

    invoke-static {v0, v3}, Lkotlin/jvm/internal/Intrinsics;->checkNotNull(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast v0, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    invoke-virtual {v0}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object v0

    invoke-virtual {v0}, Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;->clearAll()V

    const/4 v0, 0x0

    .line 97
    sput-boolean v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->isRunning:Z

    .line 98
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->serviceJob:Lkotlinx/coroutines/CompletableJob;

    check-cast p0, Lkotlinx/coroutines/Job;

    invoke-static {p0, v2, v1, v2}, Lkotlinx/coroutines/Job$DefaultImpls;->cancel$default(Lkotlinx/coroutines/Job;Ljava/util/concurrent/CancellationException;ILjava/lang/Object;)V

    return-void
.end method

.method private final scheduleClear(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/lang/String;)V
    .registers 11

    .line 236
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->clearUnlockJob:Lkotlinx/coroutines/Job;

    if-eqz v0, :cond_c

    invoke-interface {v0}, Lkotlinx/coroutines/Job;->isActive()Z

    move-result v0

    const/4 v1, 0x1

    if-ne v0, v1, :cond_c

    return-void

    .line 238
    :cond_c
    iget-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->serviceScope:Lkotlinx/coroutines/CoroutineScope;

    new-instance v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$scheduleClear$1;

    const/4 v1, 0x0

    invoke-direct {v0, p1, p2, p0, v1}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$scheduleClear$1;-><init>(Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Ljava/lang/String;Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lkotlin/coroutines/Continuation;)V

    move-object v5, v0

    check-cast v5, Lkotlin/jvm/functions/Function2;

    const/4 v6, 0x3

    const/4 v7, 0x0

    const/4 v3, 0x0

    const/4 v4, 0x0

    invoke-static/range {v2 .. v7}, Lkotlinx/coroutines/BuildersKt;->launch$default(Lkotlinx/coroutines/CoroutineScope;Lkotlin/coroutines/CoroutineContext;Lkotlinx/coroutines/CoroutineStart;Lkotlin/jvm/functions/Function2;ILjava/lang/Object;)Lkotlinx/coroutines/Job;

    move-result-object p1

    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->clearUnlockJob:Lkotlinx/coroutines/Job;

    return-void
.end method

.method private final startAsForegroundIfPossible()V
    .registers 3

    .line 253
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->createNotificationChannel()V

    .line 255
    :try_start_3
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->buildNotification()Landroid/app/Notification;

    move-result-object v0

    const/16 v1, 0x1005

    invoke-virtual {p0, v1, v0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->startForeground(ILandroid/app/Notification;)V
    :try_end_c
    .catch Ljava/lang/RuntimeException; {:try_start_3 .. :try_end_c} :catch_c

    :catch_c
    return-void
.end method

.method private final startMonitoring()V
    .registers 14

    .line 104
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->monitorJob:Lkotlinx/coroutines/Job;

    if-eqz v0, :cond_c

    invoke-interface {v0}, Lkotlinx/coroutines/Job;->isActive()Z

    move-result v0

    const/4 v1, 0x1

    if-ne v0, v1, :cond_c

    return-void

    .line 105
    :cond_c
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    const-string v1, "null cannot be cast to non-null type com.aurora.calculatorvault.app.CalculatorVaultApp"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNull(Ljava/lang/Object;Ljava/lang/String;)V

    move-object v5, v0

    check-cast v5, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    .line 106
    new-instance v3, Lcom/aurora/calculatorvault/feature/applock/domain/UsageAccessPermissionHelper;

    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    const-string v1, "getApplicationContext(...)"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-direct {v3, v0}, Lcom/aurora/calculatorvault/feature/applock/domain/UsageAccessPermissionHelper;-><init>(Landroid/content/Context;)V

    .line 107
    new-instance v4, Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;

    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-direct {v4, v0}, Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;-><init>(Landroid/content/Context;)V

    .line 108
    new-instance v6, Lcom/aurora/calculatorvault/feature/applock/domain/UsageStatsForegroundAppDetector;

    .line 109
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getApplicationContext()Landroid/content/Context;

    move-result-object v7

    invoke-static {v7, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    const/16 v11, 0xc

    const/4 v12, 0x0

    const/4 v9, 0x0

    const/4 v10, 0x0

    move-object v8, v3

    .line 108
    invoke-direct/range {v6 .. v12}, Lcom/aurora/calculatorvault/feature/applock/domain/UsageStatsForegroundAppDetector;-><init>(Landroid/content/Context;Lcom/aurora/calculatorvault/feature/applock/domain/UsageAccessPermissionHelper;Lkotlinx/coroutines/CoroutineDispatcher;Lkotlin/jvm/functions/Function0;ILkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 112
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->serviceScope:Lkotlinx/coroutines/CoroutineScope;

    new-instance v2, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$startMonitoring$1;

    const/4 v8, 0x0

    move-object v7, v6

    move-object v6, p0

    invoke-direct/range {v2 .. v8}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$startMonitoring$1;-><init>(Lcom/aurora/calculatorvault/feature/applock/domain/UsageAccessPermissionHelper;Lcom/aurora/calculatorvault/feature/applock/domain/OverlayPermissionHelper;Lcom/aurora/calculatorvault/app/CalculatorVaultApp;Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lcom/aurora/calculatorvault/feature/applock/domain/UsageStatsForegroundAppDetector;Lkotlin/coroutines/Continuation;)V

    move-object v10, v2

    check-cast v10, Lkotlin/jvm/functions/Function2;

    const/4 v11, 0x3

    move-object v7, v0

    invoke-static/range {v7 .. v12}, Lkotlinx/coroutines/BuildersKt;->launch$default(Lkotlinx/coroutines/CoroutineScope;Lkotlin/coroutines/CoroutineContext;Lkotlinx/coroutines/CoroutineStart;Lkotlin/jvm/functions/Function2;ILjava/lang/Object;)Lkotlinx/coroutines/Job;

    move-result-object p0

    iput-object p0, v6, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->monitorJob:Lkotlinx/coroutines/Job;

    return-void
.end method


# virtual methods
.method public onBind(Landroid/content/Intent;)Landroid/os/IBinder;
    .registers 2

    const/4 p0, 0x0

    return-object p0
.end method

.method public onCreate()V
    .registers 10

    .line 44
    invoke-super {p0}, Landroid/app/Service;->onCreate()V

    const/4 v0, 0x1

    .line 45
    sput-boolean v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->isRunning:Z

    .line 46
    new-instance v0, Ljava/lang/ref/WeakReference;

    invoke-direct {v0, p0}, Ljava/lang/ref/WeakReference;-><init>(Ljava/lang/Object;)V

    sput-object v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->runningService:Ljava/lang/ref/WeakReference;

    .line 48
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getApplicationContext()Landroid/content/Context;

    move-result-object v0

    const-string v1, "null cannot be cast to non-null type com.aurora.calculatorvault.app.CalculatorVaultApp"

    invoke-static {v0, v1}, Lkotlin/jvm/internal/Intrinsics;->checkNotNull(Ljava/lang/Object;Ljava/lang/String;)V

    check-cast v0, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;

    .line 49
    new-instance v1, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;

    .line 50
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->getApplicationContext()Landroid/content/Context;

    move-result-object v2

    const-string v3, "getApplicationContext(...)"

    invoke-static {v2, v3}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    .line 51
    invoke-virtual {v0}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getVaultUnlockUseCase()Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;

    move-result-object v3

    .line 52
    invoke-virtual {v0}, Lcom/aurora/calculatorvault/app/CalculatorVaultApp;->getAppLockSessionManager()Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    move-result-object v4

    .line 53
    iget-object v5, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->serviceScope:Lkotlinx/coroutines/CoroutineScope;

    const/16 v7, 0x10

    const/4 v8, 0x0

    const/4 v6, 0x0

    .line 49
    invoke-direct/range {v1 .. v8}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;-><init>(Landroid/content/Context;Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;Lkotlinx/coroutines/CoroutineScope;Lkotlin/jvm/functions/Function1;ILkotlin/jvm/internal/DefaultConstructorMarker;)V

    iput-object v1, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->overlayController:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;

    .line 55
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->startAsForegroundIfPossible()V

    return-void
.end method

.method public onDestroy()V
    .registers 2

    .line 82
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->releaseMonitoring()V

    const/4 v0, 0x0

    .line 83
    sput-object v0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->runningService:Ljava/lang/ref/WeakReference;

    .line 84
    invoke-super {p0}, Landroid/app/Service;->onDestroy()V

    return-void
.end method

.method public onStartCommand(Landroid/content/Intent;II)I
    .registers 10

    const/4 p2, 0x0

    if-eqz p1, :cond_8

    .line 59
    invoke-virtual {p1}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object p1

    goto :goto_9

    :cond_8
    move-object p1, p2

    :goto_9
    if-eqz p1, :cond_22

    invoke-virtual {p1}, Ljava/lang/String;->hashCode()I

    move-result p3

    const v0, -0x4d349ed8

    if-eq p3, v0, :cond_15

    goto :goto_22

    :cond_15
    const-string p3, "com.aurora.calculatorvault.action.STOP_APP_LOCK_MONITOR"

    invoke-virtual {p1, p3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result p1

    if-eqz p1, :cond_22

    .line 61
    invoke-virtual {p0}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->stopSelf()V

    const/4 p0, 0x2

    return p0

    .line 64
    :cond_22
    :goto_22
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;->serviceScope:Lkotlinx/coroutines/CoroutineScope;

    new-instance p1, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$onStartCommand$1;

    invoke-direct {p1, p0, p2}, Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService$onStartCommand$1;-><init>(Lcom/aurora/calculatorvault/feature/applock/service/AppLockMonitorService;Lkotlin/coroutines/Continuation;)V

    move-object v3, p1

    check-cast v3, Lkotlin/jvm/functions/Function2;

    const/4 v4, 0x3

    const/4 v5, 0x0

    const/4 v1, 0x0

    const/4 v2, 0x0

    invoke-static/range {v0 .. v5}, Lkotlinx/coroutines/BuildersKt;->launch$default(Lkotlinx/coroutines/CoroutineScope;Lkotlin/coroutines/CoroutineContext;Lkotlinx/coroutines/CoroutineStart;Lkotlin/jvm/functions/Function2;ILjava/lang/Object;)Lkotlinx/coroutines/Job;

    const/4 p0, 0x1

    return p0
.end method
