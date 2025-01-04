package dev.itsvic.parceltracker.ui.views

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.itsvic.parceltracker.R
import dev.itsvic.parceltracker.api.ParcelNonExistentException
import dev.itsvic.parceltracker.api.Service
import dev.itsvic.parceltracker.api.getParcel
import dev.itsvic.parceltracker.api.serviceOptions
import dev.itsvic.parceltracker.api.serviceToHumanString
import dev.itsvic.parceltracker.db.Parcel
import dev.itsvic.parceltracker.ui.theme.ParcelTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddParcelView(
    onBackPressed: () -> Unit,
    onCompleted: (Parcel) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var humanName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var trackingId by remember { mutableStateOf("") }
    var idError by remember { mutableStateOf(false) }
    var needsPostalCode by remember { mutableStateOf(false) }
    var postalCode by remember { mutableStateOf("") }
    var postalCodeError by remember { mutableStateOf(false) }
    var service by remember { mutableStateOf(Service.UNDEFINED) }
    var serviceError by remember { mutableStateOf(false) }

    fun validateInputs(): Boolean {
        var success = true
        if (humanName.isBlank()) {
            success = false; nameError = true
        }
        if (trackingId.isBlank()) {
            success = false; idError = true
        }
        if (service == Service.UNDEFINED) {
            success = false; serviceError = true
        }
        if (needsPostalCode && postalCode.isBlank()) {
            success = false; postalCodeError = true
        }

        if (!success) return false

        try {
            getParcel(trackingId, if (needsPostalCode) postalCode else null, service)
        } catch (e: IOException) {
             // network exception
            Log.w("AddParcelView", "Network exception during validation: $e")
            coroutineScope.launch {
                Toast.makeText(context, R.string.network_failure_detail, Toast.LENGTH_LONG).show()
            }
            return false
        } catch (e: ParcelNonExistentException) {
            coroutineScope.launch {
                Toast.makeText(context, R.string.parcel_doesnt_exist_detail, Toast.LENGTH_LONG)
                    .show()
            }
            return false
        }

        return true
    }

    var expanded by remember { mutableStateOf(false) }
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.add_a_parcel))
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.go_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .sizeIn(maxWidth = 488.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = humanName,
                    onValueChange = { humanName = it; nameError = false },
                    singleLine = true,
                    label = { Text(stringResource(R.string.parcel_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = nameError,
                    supportingText = {
                        if (nameError) Text(stringResource(R.string.human_name_error_text))
                    }
                )

                OutlinedTextField(
                    value = trackingId,
                    onValueChange = { trackingId = it; idError = false },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tracking_id)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = idError,
                    supportingText = {
                        if (idError) Text(stringResource(R.string.tracking_id_error_text))
                    }
                )

                // Service dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = if (service == Service.UNDEFINED) "" else stringResource(
                            serviceToHumanString[service]!!
                        ),
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        readOnly = true,
                        label = { Text(stringResource(R.string.delivery_service)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        isError = serviceError,
                        supportingText = {
                            if (serviceError) Text(stringResource(R.string.service_error_text))
                        }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }) {
                        serviceOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(stringResource(serviceToHumanString[option]!!)) },
                                onClick = {
                                    service = option
                                    expanded = false
                                    serviceError = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                        Text(stringResource(R.string.specify_a_postal_code))
                        Text(
                            stringResource(R.string.specify_postal_code_flavor_text),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = needsPostalCode,
                        onCheckedChange = { needsPostalCode = it },
                    )
                }

                AnimatedVisibility(needsPostalCode) {
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it; postalCodeError = false },
                        singleLine = true,
                        label = { Text(stringResource(R.string.postal_code)) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = postalCodeError,
                        supportingText = {
                            if (postalCodeError) Text(stringResource(R.string.postal_code_error_text))
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val isOk = validateInputs()
                            if (isOk) {
                                // data valid, pass it along
                                onCompleted(
                                    Parcel(
                                        humanName = humanName,
                                        parcelId = trackingId,
                                        service = service,
                                        postalCode = if (needsPostalCode) postalCode else null
                                    )
                                )
                            }
                        }
                    }) {
                        Text(stringResource(R.string.add_parcel))
                    }
                }

            }
        }
    }
}

@Composable
@PreviewLightDark
@Preview(locale = "pl", name = "Polish")
fun AddParcelViewPreview() {
    ParcelTrackerTheme {
        AddParcelView(
            onBackPressed = {},
            onCompleted = {},
        )
    }
}
