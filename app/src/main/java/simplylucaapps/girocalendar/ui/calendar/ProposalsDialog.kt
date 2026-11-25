package simplylucaapps.girocalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import simplylucaapps.girocalendar.R
import simplylucaapps.girocalendar.data.remote.firestore.RideProposal
import simplylucaapps.girocalendar.ui.common.DifficultyBadge

/**
 * Coda delle proposte pubbliche in attesa (owner/editor): ognuna può essere approvata
 * (diventa un [RideEvent][simplylucaapps.girocalendar.data.remote.firestore.RideEvent] vero
 * nel calendario) o rifiutata (eliminata senza pubblicarla).
 */
@Composable
fun ProposalsDialog(
    proposals: List<RideProposal>,
    onDismiss: () -> Unit,
    onApprove: (RideProposal) -> Unit,
    onReject: (RideProposal) -> Unit,
    onReview: (RideProposal) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
                Text(
                    stringResource(R.string.proposals_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (proposals.isEmpty()) stringResource(R.string.proposals_empty)
                    else stringResource(R.string.proposals_pending_count, proposals.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(proposals, key = { it.id }) { p ->
                        Box(Modifier.padding(vertical = 6.dp)) {
                            ProposalCard(p, onApprove = { onApprove(p) }, onReject = { onReject(p) }, onReview = { onReview(p) })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

@Composable
private fun ProposalCard(p: RideProposal, onApprove: () -> Unit, onReject: () -> Unit, onReview: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                p.titolo, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            if (p.difficolta.isNotBlank()) DifficultyBadge(p.difficolta)
        }
        Spacer(Modifier.height(4.dp))
        val subtitle = buildString {
            if (p.data.isNotBlank()) append(p.data)
            if (p.ora.isNotBlank()) { if (isNotEmpty()) append(" · "); append(p.ora) }
            if (p.localita.isNotBlank()) { if (isNotEmpty()) append(" · "); append(p.localita) }
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (p.proponente.isNotBlank()) {
            Text(
                stringResource(R.string.proposal_by, p.proponente),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (p.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(p.note, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_reject), style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = onReview, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_review), style = MaterialTheme.typography.labelMedium)
            }
            Button(onClick = onApprove, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_approve), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
