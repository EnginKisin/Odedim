package com.example.odedim

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.odedim.ui.theme.OdedimTheme
import java.time.Duration
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun OdedimApp(resumeToken: Int = 0) {
    var legacyPayments by rememberSaveable(stateSaver = PaymentListSaver) {
        mutableStateOf(emptyList<Payment>())
    }
    var showAddPayment by rememberSaveable { mutableStateOf(false) }
    var legacySalaryDay by rememberSaveable { mutableStateOf(5) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var historyPaymentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val paymentStore = remember { PaymentStore.get(context) }
    val state by paymentStore.state.collectAsState(initial = null)
    var initialized by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val errorMessage = stringResource(R.string.operation_failed)
    val setupMessage = stringResource(R.string.setup_snackbar)
    val settingsLabel = stringResource(R.string.settings)

    fun update(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            paymentStore.importLegacy(legacyPayments, legacySalaryDay)
            legacyPayments = emptyList()
            legacySalaryDay = 5
            initialized = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            snackbar.showSnackbar(errorMessage)
        }
    }
    LaunchedEffect(resumeToken) {
        while (true) {
            val now = ZonedDateTime.now()
            today = now.toLocalDate()
            val midnight = today.plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, midnight).toMillis().coerceAtLeast(1_000))
        }
    }
    LaunchedEffect(resumeToken, initialized) {
        if (initialized) {
            try {
                paymentStore.reschedule()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage)
            }
        }
    }

    val appState = state
    val unreadThroughId = appState?.notificationHistory?.filter { !it.isRead }?.maxOfOrNull { it.id }
    LaunchedEffect(showNotifications, unreadThroughId) {
        if (showNotifications && unreadThroughId != null) {
            try {
                paymentStore.markNotificationsRead(unreadThroughId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage)
            }
        }
    }
    HomeScreen(
        payments = appState?.payments.orEmpty(),
        salaryDay = appState?.salaryDay ?: 5,
        today = today,
        loading = !initialized || appState == null,
        busy = busy,
        snackbar = snackbar,
        unreadCount = appState?.notificationHistory.orEmpty().count { !it.isRead },
        onAddPayment = {
            editingId = null; historyPaymentId = null; showNotifications = false; showSummary = false; showSettings = false; showAddPayment = true
        },
        onSettings = {
            editingId = null; historyPaymentId = null; showNotifications = false; showSummary = false; showAddPayment = false; showSettings = true
        },
        onSummary = {
            editingId = null; historyPaymentId = null; showNotifications = false; showAddPayment = false; showSettings = false; showSummary = true
        },
        onNotifications = {
            editingId = null; historyPaymentId = null; showAddPayment = false
            showSettings = false; showSummary = false; showNotifications = true
        },
        onPaid = { id -> update { paymentStore.markPaid(id) } },
        onHistory = { historyPaymentId = it },
        onEdit = { payment -> editingId = payment.id },
        onDelete = { payment -> deletingId = payment.id }
    )

    if (initialized && appState != null) {
        val editing = appState.payments.firstOrNull { it.id == editingId }
        val historyPayment = appState.payments.firstOrNull { it.id == historyPaymentId }
        when {
            showNotifications -> NotificationsScreen(
                records = appState.notificationHistory, busy = busy,
                onClose = { if (!busy) showNotifications = false },
                onClear = { update { paymentStore.clearNotifications() } }
            )
            showSummary -> SummaryScreen(appState.payments, today, onClose = { showSummary = false })
            showAddPayment || editing != null -> {
                AddPaymentDialog(
                    salaryDay = appState.salaryDay,
                    busy = busy,
                    payment = editing,
                    onSalaryChange = { day -> update { paymentStore.setSalaryDay(day) } },
                    onDismiss = { if (!busy) { showAddPayment = false; editingId = null } },
                    onSave = { name, amount, rule, selectedDate, time, currency, firstDueDate ->
                        update {
                            if (editing == null) {
                                paymentStore.add(name, amount, rule, selectedDate, time, currency, firstDueDate)
                            } else {
                                paymentStore.edit(editing.id, name, amount, rule, requireNotNull(selectedDate), time, currency)
                            }
                            showAddPayment = false
                            editingId = null
                            val scheduler = ReminderScheduler(context)
                            if (appState.notificationsEnabled &&
                                (!scheduler.notificationsAllowed() || !scheduler.exactAlarmsAllowed())) {
                                scope.launch {
                                    if (snackbar.showSnackbar(setupMessage, settingsLabel) ==
                                        SnackbarResult.ActionPerformed) showSettings = true
                                }
                            }
                        }
                    }
                )
            }
            showSettings -> {
                SalarySettingsDialog(
                    salaryDay = appState.salaryDay,
                    notificationsEnabled = appState.notificationsEnabled,
                    busy = busy,
                    resumeToken = resumeToken,
                    onDismiss = { if (!busy) showSettings = false },
                    onSave = { day, enabled -> update { paymentStore.saveSettings(day, enabled) } }
                )
            }
            historyPayment != null -> {
                PaymentHistoryDialog(
                    payment = historyPayment, busy = busy,
                    onDismiss = { if (!busy) historyPaymentId = null },
                    onUndo = { update { paymentStore.undo(historyPayment.id) } },
                    onCompleteAmount = { index, record, amount, currency ->
                        update { paymentStore.completeHistoryAmount(historyPayment.id, index, record, amount, currency) }
                    }
                )
            }
        }
        appState.payments.firstOrNull { it.id == deletingId }?.let { deleting ->
            AlertDialog(
                onDismissRequest = { if (!busy) deletingId = null },
                title = { Text(stringResource(R.string.delete_payment_title)) },
                text = { Text(stringResource(R.string.delete_payment_message, deleting.name)) },
                confirmButton = {
                    TextButton(enabled = !busy, onClick = {
                        update {
                            paymentStore.delete(deleting.id)
                            deletingId = null
                            if (historyPaymentId == deleting.id) historyPaymentId = null
                            if (editingId == deleting.id) editingId = null
                        }
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { deletingId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    payments: List<Payment>,
    salaryDay: Int = 5,
    today: LocalDate = LocalDate.now(),
    loading: Boolean = false,
    busy: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onAddPayment: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSummary: () -> Unit = {},
    unreadCount: Int = 0,
    onNotifications: () -> Unit = {},
    onPaid: (Long) -> Unit = {},
    onHistory: (Long) -> Unit = {},
    onEdit: (Payment) -> Unit = {},
    onDelete: (Payment) -> Unit = {}
) {
    var filters by rememberSaveable(stateSaver = PaymentListFilterSaver) { mutableStateOf(PaymentListFilter()) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var requestSearchFocus by remember { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val visiblePayments = remember(payments, filters, query, today) { filters.apply(payments, query, today) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.primary) },
                actions = {
                    TextButton(onClick = onSummary, enabled = !loading && !busy) {
                        Text(stringResource(R.string.summary_title))
                    }
                    TextButton(onClick = onSettings, enabled = !loading && !busy) {
                        Text(stringResource(R.string.settings))
                    }
                    IconButton(onClick = onNotifications, enabled = !loading && !busy) {
                        BadgedBox(badge = {
                            if (unreadCount > 0) {
                                Badge(
                                    modifier = Modifier.clearAndSetSemantics {},
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) { Text(unreadCount.toString()) }
                            }
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_notifications),
                                if (unreadCount > 0) stringResource(R.string.notification_inbox_unread, unreadCount)
                                else stringResource(R.string.notification_inbox)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface {
                Button(
                    onClick = onAddPayment,
                    enabled = !loading && !busy,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) { Text(stringResource(R.string.add_payment)) }
            }
        }
    ) { innerPadding ->
        if (loading || payments.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center) {
                if (!loading) {
                    Text(stringResource(R.string.payment_list_count, payments.size),
                        modifier = Modifier.align(Alignment.TopStart),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!loading) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(72.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✓", style = MaterialTheme.typography.headlineLarge)
                            }
                        }
                    }
                    Text(stringResource(if (loading) R.string.loading else R.string.no_payments),
                        style = MaterialTheme.typography.titleLarge)
                    if (!loading) Text(stringResource(R.string.add_first_payment),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
                    top = innerPadding.calculateTopPadding() + 20.dp,
                    bottom = innerPadding.calculateBottomPadding() + 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "controls") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (query.isNotBlank() || filters.activeCount > 0) {
                                stringResource(R.string.payment_list_filtered_count, visiblePayments.size, payments.size)
                            } else stringResource(R.string.payment_list_count, payments.size),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = { showSearch = true; requestSearchFocus = true }, enabled = !busy) {
                            Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search_payments))
                        }
                        TextButton(onClick = { focus.clearFocus(); showFilters = true }, enabled = !busy) {
                            Text(if (filters.activeCount > 0) stringResource(R.string.filter_active, filters.activeCount)
                                else stringResource(R.string.filter_payments))
                        }
                    }
                }
                if (showSearch || query.isNotEmpty()) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = query,
                            onValueChange = {
                                query = it
                                scope.launch { listState.scrollToItem(0) }
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.search_payments)) },
                            placeholder = { Text(stringResource(R.string.search_payment_hint)) },
                            trailingIcon = {
                                IconButton(onClick = { query = ""; showSearch = false; focus.clearFocus() }) {
                                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close_search))
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus)
                        )
                        LaunchedEffect(requestSearchFocus) {
                            if (requestSearchFocus) {
                                searchFocus.requestFocus()
                                requestSearchFocus = false
                            }
                        }
                    }
                }
                if (visiblePayments.isEmpty()) {
                    item(key = "no_results") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.no_matching_payments),
                                style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = {
                                filters = PaymentListFilter(); query = ""; showSearch = false; focus.clearFocus()
                            }) { Text(stringResource(R.string.clear_search_and_filters)) }
                        }
                    }
                }
                items(visiblePayments, key = { it.id }) { payment ->
                    PaymentCard(payment, salaryDay, today, busy,
                        onPaid = { onPaid(payment.id) },
                        onHistory = { onHistory(payment.id) }, onEdit = { onEdit(payment) },
                        onDelete = { onDelete(payment) })
                }
            }
        }
    }
    if (showFilters) {
        PaymentFilterPage(
            initial = filters, onClose = { showFilters = false },
            onApply = {
                filters = it
                showFilters = false
                scope.launch { listState.scrollToItem(0) }
            }
        )
    }
}

@Composable
private fun PaymentCard(
    payment: Payment, salaryDay: Int, today: LocalDate, busy: Boolean,
    onPaid: () -> Unit, onHistory: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val locked = payment.isPaymentLocked(today)
    val formattedAmount = TurkishMoney.formatCurrency(payment.amount, payment.currency)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )) {
        Column(Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(payment.name, style = MaterialTheme.typography.titleMedium)
                    Text(formattedAmount, style = MaterialTheme.typography.headlineSmall)
                }
                Box {
                    IconButton(onClick = { showMenu = true }, enabled = !busy) {
                        Icon(painterResource(R.drawable.ic_more_vert),
                            stringResource(R.string.payment_actions))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_payment)) },
                            onClick = { showMenu = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.payment_history_simple)) },
                            onClick = { showMenu = false; onHistory() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_payment),
                                color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() })
                    }
                }
            }
            Text(repeatRuleLabel(payment.repeatRule, salaryDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.next_payment_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(payment.paymentTime?.let {
                        stringResource(R.string.date_time_value,
                            formatPaymentDate(payment.nextDueDate), formatPaymentTime(it))
                    } ?: formatPaymentDate(payment.nextDueDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (payment.nextDueDate < LocalDate.now()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface)
                }
                Button(onClick = onPaid, enabled = !busy && payment.paymentTime != null && !locked) {
                    Text(stringResource(if (locked) R.string.payment_paid else R.string.mark_as_paid))
                }
            }
            if (payment.paymentTime == null) {
                TextButton(onClick = onEdit, enabled = !busy) {
                    Text(stringResource(R.string.choose_required_time))
                }
            }
        }
    }
}

@Composable
internal fun repeatRuleLabel(rule: RepeatRule, salaryDay: Int): String = when (rule.type) {
    RepeatType.AFTER_30_DAYS -> stringResource(R.string.repeat_after_30_days)
    RepeatType.MONTHLY_DAY -> stringResource(R.string.repeat_monthly_summary, rule.monthlyDay)
    RepeatType.SALARY_DAY -> stringResource(R.string.repeat_salary_summary, salaryDay)
}

internal fun formatPaymentDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

internal fun formatPaymentTime(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("HH:mm"))

@Preview(showBackground = true, locale = "tr")
@Composable
private fun HomeScreenPreview() {
    OdedimTheme {
        HomeScreen(unreadCount = 3, payments = listOf(Payment(
            1, "Elektrik faturası", "850.50", LocalDate.of(2026, 12, 5),
            RepeatRule(RepeatType.MONTHLY_DAY, 5), emptyList(), LocalTime.of(18, 30)
        )))
    }
}

@Preview(showBackground = true, locale = "tr")
@Composable
private fun EmptyHomeScreenPreview() {
    OdedimTheme { HomeScreen(payments = emptyList()) }
}
