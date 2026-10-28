.class public final Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;
.super Ljava/lang/Object;
.source "AppLockOverlayController.kt"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$Companion;,
        Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$OverlayViewTreeOwner;
    }
.end annotation

.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u0082\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010\u0002\n\u0002\u0008\u0003\n\u0002\u0018\u0002\n\u0002\u0008\u0002\n\u0002\u0010\u0019\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0008\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0008\u0003\n\u0002\u0010\u000b\n\u0002\u0008\u0005\n\u0002\u0010\t\n\u0002\u0008\u0008\n\u0002\u0018\u0002\n\u0002\u0008\u0005\u0008\u0007\u0018\u0000 72\u00020\u0001:\u000267B=\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\u0008\u001a\u00020\t\u0012\u0014\u0008\u0002\u0010\n\u001a\u000e\u0012\u0004\u0012\u00020\u000c\u0012\u0004\u0012\u00020\r0\u000b\u00a2\u0006\u0004\u0008\u000e\u0010\u000fJ\u0006\u0010$\u001a\u00020%J\u0008\u0010&\u001a\u0004\u0018\u00010\u000cJ(\u0010\'\u001a\u00020%2\u0006\u0010(\u001a\u00020\u000c2\u0008\u0010)\u001a\u0004\u0018\u00010\u000c2\u0006\u0010*\u001a\u00020+H\u0086@\u00a2\u0006\u0002\u0010,J\u0006\u0010-\u001a\u00020\rJ\u0010\u0010.\u001a\u00020\r2\u0006\u0010/\u001a\u00020\u0019H\u0002J\u0008\u00100\u001a\u00020\rH\u0002J\u0008\u00101\u001a\u00020\rH\u0002J\u0008\u00102\u001a\u00020\rH\u0002J\u0008\u00103\u001a\u000204H\u0002J\u0008\u00105\u001a\u00020\rH\u0002R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0008\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001a\u0010\n\u001a\u000e\u0012\u0004\u0012\u00020\u000c\u0012\u0004\u0012\u00020\r0\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0010\u001a\n \u0012*\u0004\u0018\u00010\u00110\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0015\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00170\u0016X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0019X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001a\u001a\u0004\u0018\u00010\u001bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001c\u001a\u0004\u0018\u00010\u001dX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u001e\u001a\u0004\u0018\u00010\u001fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0019\u0010 \u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\u00170!\u00a2\u0006\u0008\n\u0000\u001a\u0004\u0008\"\u0010#\u00a8\u00068\u00b2\u0006\u000c\u00109\u001a\u0004\u0018\u00010\u0017X\u008a\u0084\u0002"
    }
    d2 = {
        "Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;",
        "",
        "context",
        "Landroid/content/Context;",
        "vaultUnlockUseCase",
        "Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;",
        "sessionManager",
        "Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;",
        "scope",
        "Lkotlinx/coroutines/CoroutineScope;",
        "onUnlocked",
        "Lkotlin/Function1;",
        "",
        "",
        "<init>",
        "(Landroid/content/Context;Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;Lkotlinx/coroutines/CoroutineScope;Lkotlin/jvm/functions/Function1;)V",
        "windowManager",
        "Landroid/view/WindowManager;",
        "kotlin.jvm.PlatformType",
        "password",
        "",
        "_state",
        "Lkotlinx/coroutines/flow/MutableStateFlow;",
        "Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;",
        "passwordLength",
        "",
        "overlayView",
        "Landroidx/compose/ui/platform/ComposeView;",
        "overlayOwner",
        "Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$OverlayViewTreeOwner;",
        "verifyJob",
        "Lkotlinx/coroutines/Job;",
        "state",
        "Lkotlinx/coroutines/flow/StateFlow;",
        "getState",
        "()Lkotlinx/coroutines/flow/StateFlow;",
        "isShowing",
        "",
        "currentTargetPackage",
        "show",
        "targetPackageName",
        "targetAppName",
        "detectedAtElapsed",
        "",
        "(Ljava/lang/String;Ljava/lang/String;JLkotlin/coroutines/Continuation;)Ljava/lang/Object;",
        "hide",
        "inputDigit",
        "digit",
        "deleteDigit",
        "clearInput",
        "verifyPassword",
        "layoutParams",
        "Landroid/view/WindowManager$LayoutParams;",
        "wipePassword",
        "OverlayViewTreeOwner",
        "Companion",
        "app",
        "uiState"
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

.field private static final Companion:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$Companion;

.field public static final MAX_PASSWORD_LENGTH:I = 0x8
    .annotation runtime Ljava/lang/Deprecated;
    .end annotation
.end field

.field public static final MIN_PASSWORD_LENGTH:I = 0x4
    .annotation runtime Ljava/lang/Deprecated;
    .end annotation
.end field

.field public static final NULL_CHAR:C = '\u0000'
    .annotation runtime Ljava/lang/Deprecated;
    .end annotation
.end field

.field public static final TAG:Ljava/lang/String; = "CV_APPLOCK"
    .annotation runtime Ljava/lang/Deprecated;
    .end annotation
.end field


# instance fields
.field private final _state:Lkotlinx/coroutines/flow/MutableStateFlow;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lkotlinx/coroutines/flow/MutableStateFlow<",
            "Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;",
            ">;"
        }
    .end annotation
.end field

.field private final context:Landroid/content/Context;

.field private final onUnlocked:Lkotlin/jvm/functions/Function1;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lkotlin/jvm/functions/Function1<",
            "Ljava/lang/String;",
            "Lkotlin/Unit;",
            ">;"
        }
    .end annotation
.end field

.field private overlayOwner:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$OverlayViewTreeOwner;

.field private overlayView:Landroidx/compose/ui/platform/ComposeView;

.field private final password:[C

.field private passwordLength:I

.field private final scope:Lkotlinx/coroutines/CoroutineScope;

.field private final sessionManager:Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

.field private final state:Lkotlinx/coroutines/flow/StateFlow;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Lkotlinx/coroutines/flow/StateFlow<",
            "Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;",
            ">;"
        }
    .end annotation
.end field

.field private final vaultUnlockUseCase:Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;

.field private verifyJob:Lkotlinx/coroutines/Job;

.field private final windowManager:Landroid/view/WindowManager;


# direct methods
.method static constructor <clinit>()V
    .registers 2

    new-instance v0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$Companion;

    const/4 v1, 0x0

    invoke-direct {v0, v1}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$Companion;-><init>(Lkotlin/jvm/internal/DefaultConstructorMarker;)V

    sput-object v0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->Companion:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$Companion;

    const/16 v0, 0x8

    sput v0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->$stable:I

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;Lkotlinx/coroutines/CoroutineScope;Lkotlin/jvm/functions/Function1;)V
    .registers 7
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Landroid/content/Context;",
            "Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;",
            "Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;",
            "Lkotlinx/coroutines/CoroutineScope;",
            "Lkotlin/jvm/functions/Function1<",
            "-",
            "Ljava/lang/String;",
            "Lkotlin/Unit;",
            ">;)V"
        }
    .end annotation

    const-string v0, "context"

    invoke-static {p1, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "vaultUnlockUseCase"

    invoke-static {p2, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "sessionManager"

    invoke-static {p3, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "scope"

    invoke-static {p4, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, "onUnlocked"

    invoke-static {p5, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    .line 32
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 33
    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->context:Landroid/content/Context;

    .line 34
    iput-object p2, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->vaultUnlockUseCase:Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;

    .line 35
    iput-object p3, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->sessionManager:Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    .line 36
    iput-object p4, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->scope:Lkotlinx/coroutines/CoroutineScope;

    .line 37
    iput-object p5, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->onUnlocked:Lkotlin/jvm/functions/Function1;

    .line 39
    const-class p2, Landroid/view/WindowManager;

    invoke-virtual {p1, p2}, Landroid/content/Context;->getSystemService(Ljava/lang/Class;)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Landroid/view/WindowManager;

    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->windowManager:Landroid/view/WindowManager;

    const/16 p1, 0x8

    .line 40
    new-array p1, p1, [C

    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->password:[C

    const/4 p1, 0x0

    .line 41
    invoke-static {p1}, Lkotlinx/coroutines/flow/StateFlowKt;->MutableStateFlow(Ljava/lang/Object;)Lkotlinx/coroutines/flow/MutableStateFlow;

    move-result-object p1

    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    .line 47
    invoke-static {p1}, Lkotlinx/coroutines/flow/FlowKt;->asStateFlow(Lkotlinx/coroutines/flow/MutableStateFlow;)Lkotlinx/coroutines/flow/StateFlow;

    move-result-object p1

    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->state:Lkotlinx/coroutines/flow/StateFlow;

    return-void
.end method

.method public synthetic constructor <init>(Landroid/content/Context;Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;Lkotlinx/coroutines/CoroutineScope;Lkotlin/jvm/functions/Function1;ILkotlin/jvm/internal/DefaultConstructorMarker;)V
    .registers 14

    and-int/lit8 p6, p6, 0x10

    if-eqz p6, :cond_9

    .line 37
    new-instance p5, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$$ExternalSyntheticLambda0;

    invoke-direct {p5}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$$ExternalSyntheticLambda0;-><init>()V

    :cond_9
    move-object v0, p0

    move-object v1, p1

    move-object v2, p2

    move-object v3, p3

    move-object v4, p4

    move-object v5, p5

    .line 32
    invoke-direct/range {v0 .. v5}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;-><init>(Landroid/content/Context;Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;Lkotlinx/coroutines/CoroutineScope;Lkotlin/jvm/functions/Function1;)V

    return-void
.end method

.method static final _init_$lambda$0(Ljava/lang/String;)Lkotlin/Unit;
    .registers 2

    const-string v0, "it"

    invoke-static {p0, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    .line 37
    sget-object p0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object p0
.end method

.method public static final synthetic access$clearInput(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)V
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->clearInput()V

    return-void
.end method

.method public static final synthetic access$deleteDigit(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)V
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->deleteDigit()V

    return-void
.end method

.method public static final synthetic access$getContext$p(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)Landroid/content/Context;
    .registers 1

    .line 32
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->context:Landroid/content/Context;

    return-object p0
.end method

.method public static final synthetic access$getOnUnlocked$p(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)Lkotlin/jvm/functions/Function1;
    .registers 1

    .line 32
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->onUnlocked:Lkotlin/jvm/functions/Function1;

    return-object p0
.end method

.method public static final synthetic access$getSessionManager$p(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;
    .registers 1

    .line 32
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->sessionManager:Lcom/aurora/calculatorvault/feature/applock/domain/AppLockSessionManager;

    return-object p0
.end method

.method public static final synthetic access$getVaultUnlockUseCase$p(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;
    .registers 1

    .line 32
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->vaultUnlockUseCase:Lcom/aurora/calculatorvault/feature/calculator/domain/VaultUnlockUseCase;

    return-object p0
.end method

.method public static final synthetic access$getWindowManager$p(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)Landroid/view/WindowManager;
    .registers 1

    .line 32
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->windowManager:Landroid/view/WindowManager;

    return-object p0
.end method

.method public static final synthetic access$get_state$p(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)Lkotlinx/coroutines/flow/MutableStateFlow;
    .registers 1

    .line 32
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    return-object p0
.end method

.method public static final synthetic access$inputDigit(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;I)V
    .registers 2

    .line 32
    invoke-direct {p0, p1}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->inputDigit(I)V

    return-void
.end method

.method public static final synthetic access$layoutParams(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)Landroid/view/WindowManager$LayoutParams;
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->layoutParams()Landroid/view/WindowManager$LayoutParams;

    move-result-object p0

    return-object p0
.end method

.method public static final synthetic access$setOverlayOwner$p(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$OverlayViewTreeOwner;)V
    .registers 2

    .line 32
    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->overlayOwner:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$OverlayViewTreeOwner;

    return-void
.end method

.method public static final synthetic access$setOverlayView$p(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;Landroidx/compose/ui/platform/ComposeView;)V
    .registers 2

    .line 32
    iput-object p1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->overlayView:Landroidx/compose/ui/platform/ComposeView;

    return-void
.end method

.method public static final synthetic access$verifyPassword(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)V
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->verifyPassword()V

    return-void
.end method

.method public static final synthetic access$wipePassword(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;)V
    .registers 1

    .line 32
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->wipePassword()V

    return-void
.end method

.method private final clearInput()V
    .registers 10

    .line 166
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    invoke-interface {v0}, Lkotlinx/coroutines/flow/MutableStateFlow;->getValue()Ljava/lang/Object;

    move-result-object v0

    move-object v1, v0

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    if-nez v1, :cond_c

    goto :goto_12

    .line 167
    :cond_c
    invoke-virtual {v1}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->getVerifying()Z

    move-result v0

    if-eqz v0, :cond_13

    :goto_12
    return-void

    .line 168
    :cond_13
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->wipePassword()V

    .line 169
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    const/16 v7, 0xb

    const/4 v8, 0x0

    const/4 v2, 0x0

    const/4 v3, 0x0

    const/4 v4, 0x0

    const/4 v5, 0x0

    const/4 v6, 0x0

    invoke-static/range {v1 .. v8}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->copy$default(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;Ljava/lang/String;Ljava/lang/String;IZZILjava/lang/Object;)Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    move-result-object v0

    invoke-interface {p0, v0}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V

    return-void
.end method

.method private final deleteDigit()V
    .registers 10

    .line 156
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    invoke-interface {v0}, Lkotlinx/coroutines/flow/MutableStateFlow;->getValue()Ljava/lang/Object;

    move-result-object v0

    move-object v1, v0

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    if-nez v1, :cond_c

    goto :goto_12

    .line 157
    :cond_c
    invoke-virtual {v1}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->getVerifying()Z

    move-result v0

    if-eqz v0, :cond_13

    :goto_12
    return-void

    .line 158
    :cond_13
    iget v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->passwordLength:I

    if-lez v0, :cond_20

    iget-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->password:[C

    add-int/lit8 v0, v0, -0x1

    iput v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->passwordLength:I

    const/4 v3, 0x0

    aput-char v3, v2, v0

    .line 159
    :cond_20
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    .line 160
    iget v4, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->passwordLength:I

    const/16 v7, 0xb

    const/4 v8, 0x0

    const/4 v2, 0x0

    const/4 v3, 0x0

    const/4 v5, 0x0

    const/4 v6, 0x0

    .line 159
    invoke-static/range {v1 .. v8}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->copy$default(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;Ljava/lang/String;Ljava/lang/String;IZZILjava/lang/Object;)Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    move-result-object p0

    invoke-interface {v0, p0}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V

    return-void
.end method

.method private final inputDigit(I)V
    .registers 11

    .line 146
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    invoke-interface {v0}, Lkotlinx/coroutines/flow/MutableStateFlow;->getValue()Ljava/lang/Object;

    move-result-object v0

    move-object v1, v0

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    if-nez v1, :cond_c

    goto :goto_3a

    .line 147
    :cond_c
    invoke-virtual {v1}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->getVerifying()Z

    move-result v0

    if-nez v0, :cond_3a

    if-ltz p1, :cond_3a

    const/16 v0, 0xa

    if-ge p1, v0, :cond_3a

    iget v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->passwordLength:I

    const/16 v2, 0x8

    if-lt v0, v2, :cond_1f

    goto :goto_3a

    .line 148
    :cond_1f
    iget-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->password:[C

    add-int/lit8 v4, v0, 0x1

    iput v4, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->passwordLength:I

    add-int/lit8 p1, p1, 0x30

    int-to-char p1, p1

    aput-char p1, v2, v0

    .line 149
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    const/16 v7, 0xb

    const/4 v8, 0x0

    const/4 v2, 0x0

    const/4 v3, 0x0

    const/4 v5, 0x0

    const/4 v6, 0x0

    invoke-static/range {v1 .. v8}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->copy$default(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;Ljava/lang/String;Ljava/lang/String;IZZILjava/lang/Object;)Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    move-result-object p1

    invoke-interface {p0, p1}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V

    :cond_3a
    :goto_3a
    return-void
.end method

.method private final layoutParams()Landroid/view/WindowManager$LayoutParams;
    .registers 7

    .line 211
    new-instance v0, Landroid/view/WindowManager$LayoutParams;

    const/16 v4, 0x2300

    const/4 v5, -0x3

    const/4 v1, -0x1

    const/4 v2, -0x1

    const/16 v3, 0x7f6

    invoke-direct/range {v0 .. v5}, Landroid/view/WindowManager$LayoutParams;-><init>(IIIII)V

    const p0, 0x800033

    .line 220
    iput p0, v0, Landroid/view/WindowManager$LayoutParams;->gravity:I

    .line 221
    sget p0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1c

    if-lt p0, v1, :cond_1a

    const/4 p0, 0x1

    .line 222
    iput p0, v0, Landroid/view/WindowManager$LayoutParams;->layoutInDisplayCutoutMode:I

    :cond_1a
    return-object v0
.end method

.method private final verifyPassword()V
    .registers 11

    .line 176
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    invoke-interface {v0}, Lkotlinx/coroutines/flow/MutableStateFlow;->getValue()Ljava/lang/Object;

    move-result-object v0

    move-object v1, v0

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    if-nez v1, :cond_c

    goto :goto_4c

    .line 177
    :cond_c
    invoke-virtual {v1}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->getVerifying()Z

    move-result v0

    if-nez v0, :cond_4c

    iget v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->passwordLength:I

    const/4 v2, 0x4

    if-gt v2, v0, :cond_4c

    const/16 v2, 0x9

    if-ge v0, v2, :cond_4c

    .line 178
    iget-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->password:[C

    invoke-static {v2, v0}, Ljava/util/Arrays;->copyOf([CI)[C

    move-result-object v0

    const-string v2, "copyOf(...)"

    invoke-static {v0, v2}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    .line 179
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->wipePassword()V

    .line 180
    iget-object v9, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    const/4 v7, 0x3

    const/4 v8, 0x0

    const/4 v2, 0x0

    const/4 v3, 0x0

    const/4 v4, 0x0

    const/4 v5, 0x1

    const/4 v6, 0x0

    invoke-static/range {v1 .. v8}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->copy$default(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;Ljava/lang/String;Ljava/lang/String;IZZILjava/lang/Object;)Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    move-result-object v1

    invoke-interface {v9, v1}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V

    .line 182
    iget-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->scope:Lkotlinx/coroutines/CoroutineScope;

    new-instance v1, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$verifyPassword$1;

    invoke-direct {v1, p0, v0, v3}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$verifyPassword$1;-><init>(Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;[CLkotlin/coroutines/Continuation;)V

    move-object v5, v1

    check-cast v5, Lkotlin/jvm/functions/Function2;

    const/4 v6, 0x3

    const/4 v7, 0x0

    const/4 v4, 0x0

    invoke-static/range {v2 .. v7}, Lkotlinx/coroutines/BuildersKt;->launch$default(Lkotlinx/coroutines/CoroutineScope;Lkotlin/coroutines/CoroutineContext;Lkotlinx/coroutines/CoroutineStart;Lkotlin/jvm/functions/Function2;ILjava/lang/Object;)Lkotlinx/coroutines/Job;

    move-result-object v0

    iput-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->verifyJob:Lkotlinx/coroutines/Job;

    :cond_4c
    :goto_4c
    return-void
.end method

.method private final wipePassword()V
    .registers 7

    .line 228
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->password:[C

    const/4 v4, 0x6

    const/4 v5, 0x0

    const/4 v1, 0x0

    const/4 v2, 0x0

    const/4 v3, 0x0

    invoke-static/range {v0 .. v5}, Lkotlin/collections/ArraysKt;->fill$default([CCIIILjava/lang/Object;)V

    const/4 v0, 0x0

    .line 229
    iput v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->passwordLength:I

    return-void
.end method


# virtual methods
.method public final declared-synchronized currentTargetPackage()Ljava/lang/String;
    .registers 2

    monitor-enter p0

    .line 53
    :try_start_1
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    invoke-interface {v0}, Lkotlinx/coroutines/flow/MutableStateFlow;->getValue()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;

    if-eqz v0, :cond_10

    invoke-virtual {v0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;->getTargetPackageName()Ljava/lang/String;

    move-result-object v0
    :try_end_f
    .catchall {:try_start_1 .. :try_end_f} :catchall_13

    goto :goto_11

    :cond_10
    const/4 v0, 0x0

    :goto_11
    monitor-exit p0

    return-object v0

    :catchall_13
    move-exception v0

    :try_start_14
    monitor-exit p0
    :try_end_15
    .catchall {:try_start_14 .. :try_end_15} :catchall_13

    throw v0
.end method

.method public final getState()Lkotlinx/coroutines/flow/StateFlow;
    .registers 1
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()",
            "Lkotlinx/coroutines/flow/StateFlow<",
            "Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayState;",
            ">;"
        }
    .end annotation

    .line 47
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->state:Lkotlinx/coroutines/flow/StateFlow;

    return-object p0
.end method

.method public final hide()V
    .registers 6

    .line 120
    monitor-enter p0

    .line 121
    :try_start_1
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->overlayOwner:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$OverlayViewTreeOwner;

    const/4 v1, 0x0

    .line 122
    iput-object v1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->overlayOwner:Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$OverlayViewTreeOwner;
    :try_end_6
    .catchall {:try_start_1 .. :try_end_6} :catchall_4e

    .line 120
    monitor-exit p0

    .line 125
    monitor-enter p0

    .line 126
    :try_start_8
    iget-object v2, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->overlayView:Landroidx/compose/ui/platform/ComposeView;

    .line 127
    iput-object v1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->overlayView:Landroidx/compose/ui/platform/ComposeView;
    :try_end_c
    .catchall {:try_start_8 .. :try_end_c} :catchall_4b

    .line 125
    monitor-exit p0

    .line 130
    iget-object v3, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->verifyJob:Lkotlinx/coroutines/Job;

    if-eqz v3, :cond_15

    const/4 v4, 0x1

    invoke-static {v3, v1, v4, v1}, Lkotlinx/coroutines/Job$DefaultImpls;->cancel$default(Lkotlinx/coroutines/Job;Ljava/util/concurrent/CancellationException;ILjava/lang/Object;)V

    .line 131
    :cond_15
    iput-object v1, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->verifyJob:Lkotlinx/coroutines/Job;

    .line 132
    invoke-direct {p0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->wipePassword()V

    .line 133
    iget-object v3, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->_state:Lkotlinx/coroutines/flow/MutableStateFlow;

    invoke-interface {v3, v1}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V

    if-eqz v2, :cond_45

    .line 135
    :try_start_21
    sget-object v1, Lkotlin/Result;->Companion:Lkotlin/Result$Companion;

    move-object v1, p0

    check-cast v1, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;

    .line 136
    iget-object p0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->windowManager:Landroid/view/WindowManager;

    check-cast v2, Landroid/view/View;

    invoke-interface {p0, v2}, Landroid/view/WindowManager;->removeView(Landroid/view/View;)V

    .line 138
    sget-object p0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    .line 135
    invoke-static {p0}, Lkotlin/Result;->constructor-impl(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p0
    :try_end_33
    .catchall {:try_start_21 .. :try_end_33} :catchall_34

    goto :goto_3f

    :catchall_34
    move-exception p0

    sget-object v1, Lkotlin/Result;->Companion:Lkotlin/Result$Companion;

    invoke-static {p0}, Lkotlin/ResultKt;->createFailure(Ljava/lang/Throwable;)Ljava/lang/Object;

    move-result-object p0

    invoke-static {p0}, Lkotlin/Result;->constructor-impl(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object p0

    .line 138
    :goto_3f
    invoke-static {p0}, Lkotlin/Result;->exceptionOrNull-impl(Ljava/lang/Object;)Ljava/lang/Throwable;

    .line 140
    invoke-static {p0}, Lkotlin/Result;->box-impl(Ljava/lang/Object;)Lkotlin/Result;

    :cond_45
    if-eqz v0, :cond_4a

    .line 142
    invoke-virtual {v0}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$OverlayViewTreeOwner;->destroy()V

    :cond_4a
    return-void

    :catchall_4b
    move-exception v0

    .line 125
    monitor-exit p0

    throw v0

    :catchall_4e
    move-exception v0

    .line 120
    monitor-exit p0

    throw v0
.end method

.method public final declared-synchronized isShowing()Z
    .registers 2

    monitor-enter p0

    .line 50
    :try_start_1
    iget-object v0, p0, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;->overlayView:Landroidx/compose/ui/platform/ComposeView;
    :try_end_3
    .catchall {:try_start_1 .. :try_end_3} :catchall_a

    if-eqz v0, :cond_7

    const/4 v0, 0x1

    goto :goto_8

    :cond_7
    const/4 v0, 0x0

    :goto_8
    monitor-exit p0

    return v0

    :catchall_a
    move-exception v0

    :try_start_b
    monitor-exit p0
    :try_end_c
    .catchall {:try_start_b .. :try_end_c} :catchall_a

    throw v0
.end method

.method public final show(Ljava/lang/String;Ljava/lang/String;JLkotlin/coroutines/Continuation;)Ljava/lang/Object;
    .registers 14
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Ljava/lang/String;",
            "Ljava/lang/String;",
            "J",
            "Lkotlin/coroutines/Continuation<",
            "-",
            "Ljava/lang/Boolean;",
            ">;)",
            "Ljava/lang/Object;"
        }
    .end annotation

    .line 59
    invoke-static {}, Lkotlinx/coroutines/Dispatchers;->getMain()Lkotlinx/coroutines/MainCoroutineDispatcher;

    move-result-object v0

    check-cast v0, Lkotlin/coroutines/CoroutineContext;

    new-instance v1, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$show$2;

    const/4 v7, 0x0

    move-object v3, p0

    move-object v2, p1

    move-object v4, p2

    move-wide v5, p3

    invoke-direct/range {v1 .. v7}, Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController$show$2;-><init>(Ljava/lang/String;Lcom/aurora/calculatorvault/feature/applock/overlay/AppLockOverlayController;Ljava/lang/String;JLkotlin/coroutines/Continuation;)V

    check-cast v1, Lkotlin/jvm/functions/Function2;

    invoke-static {v0, v1, p5}, Lkotlinx/coroutines/BuildersKt;->withContext(Lkotlin/coroutines/CoroutineContext;Lkotlin/jvm/functions/Function2;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;

    move-result-object p0

    return-object p0
.end method
