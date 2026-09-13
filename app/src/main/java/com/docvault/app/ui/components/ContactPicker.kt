package com.docvault.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Picks one phone number from the system contacts app.
 *
 * Deliberately uses ACTION_PICK against [ContactsContract.CommonDataKinds.Phone]
 * rather than READ_CONTACTS + our own list:
 *
 *  - **No permission prompt.** The system picker runs in its own process and
 *    hands back a URI for the single row the user chose, with temporary read
 *    access. The app never gains access to the address book.
 *  - **Least privilege.** A document vault asking to read every contact is a
 *    poor trade for one phone number, and the permission would show up in the
 *    Play listing for the life of the app.
 *
 * Returns null when the user backs out.
 */
class PickPhoneNumberContract : ActivityResultContract<Unit, String?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK).apply {
            // Picking the Phone data type (not Contacts) means the user chooses
            // a specific number, so a contact with several numbers is
            // unambiguous.
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        val uri: Uri = intent?.data ?: return null
        return uri.toString()
    }
}

/**
 * Reads the phone number out of the URI the picker returned.
 *
 * The temporary grant covers exactly this row, so no permission is needed.
 * Returns null if the row is gone or unreadable rather than throwing — a
 * failed contact read must not take down the invite dialog.
 */
fun readPickedPhoneNumber(context: Context, uriString: String): String? = runCatching {
    val uri = Uri.parse(uriString)
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

/**
 * Remembers a launcher that yields the chosen number as raw text.
 *
 * The caller normalises it with [PhoneNumber.parse] — contact entries carry
 * spaces, dashes, trunk prefixes and sometimes a country code.
 */
@Composable
fun rememberContactNumberPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(PickPhoneNumberContract()) { uriString ->
        if (uriString != null) {
            readPickedPhoneNumber(context, uriString)?.let(onPicked)
        }
    }
    return { launcher.launch(Unit) }
}
