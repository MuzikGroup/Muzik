/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.muzik.shared.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.about
import com.muzik.shared.generated.resources.brand_discord
import com.muzik.shared.generated.resources.brand_github
import com.muzik.shared.generated.resources.brand_telegram
import com.muzik.shared.generated.resources.credits_lead_developer
import com.muzik.shared.generated.resources.credits_other_contributors
import com.muzik.shared.generated.resources.credits_team_section
import com.muzik.shared.generated.resources.credits_license_desc
import com.muzik.shared.generated.resources.credits_license_name
import com.muzik.shared.generated.resources.open_github_profile
import com.muzik.shared.generated.resources.open_website
import com.muzik.shared.generated.resources.sponsor_developer
import com.muzik.shared.ui.components.ModalBottomSheet
import com.muzik.shared.ui.components.PlayerMenuAction
import com.muzik.shared.ui.components.PlayerMenuPrimaryActions
import com.muzik.shared.ui.components.images.AsyncImage
import com.muzik.shared.ui.icons.MuzikIcons
import com.muzik.shared.ui.icons.MuzikIconsBrand
import org.jetbrains.compose.resources.stringResource as sharedStringResource

@Composable
fun rememberAboutScreenText(): AboutScreenText =
    AboutScreenText(
        title = sharedStringResource(Res.string.about),
        leadSection = sharedStringResource(Res.string.credits_lead_developer),
        teamSection = sharedStringResource(Res.string.credits_team_section),
        leadDeveloperRole = sharedStringResource(Res.string.credits_lead_developer),
        pipoTeamRole = sharedStringResource(Res.string.credits_other_contributors),
        discord = sharedStringResource(Res.string.brand_discord),
        telegram = sharedStringResource(Res.string.brand_telegram),
        github = sharedStringResource(Res.string.brand_github),
        license = sharedStringResource(Res.string.credits_license_name),
        licenseDescription = sharedStringResource(Res.string.credits_license_desc),
    )

enum class AboutContributorRole {
    LEAD_DEVELOPER,
    PIPO_TEAM,
}

enum class AboutAvatarShape {
    COOKIE_4_SIDED,
    COOKIE_6_SIDED,
    COOKIE_9_SIDED,
    BURST,
    PUFFY_DIAMOND,
    SUNNY,
    GEM,
}

data class AboutContributor(
    val name: String,
    val role: AboutContributorRole,
    val githubHandle: String? = null,
    val websiteUrl: String? = null,
    val sponsorUrl: String? = null,
    val avatarShape: AboutAvatarShape,
    val customAvatarUrl: String? = null,
    val favoriteSongVideoId: String? = null,
) {
    val avatarUrl: String =
        customAvatarUrl
            ?: githubHandle?.let { "https://github.com/$it.png" }
            ?: error("Contributor $name needs a GitHub handle or customAvatarUrl")
    val githubUrl: String? = githubHandle?.let { "https://github.com/$it" }
}

// Muzik is built by Ignatius (lead developer) together with the Pipo Team.
object AboutContributors {
    val leadDeveloper: AboutContributor =
        AboutContributor(
            name = "Ignatius",
            role = AboutContributorRole.LEAD_DEVELOPER,
            githubHandle = "Motius",
            sponsorUrl = "https://buymesoda.com/lllcuDCr25N1a1peiKOCl22Oaf13",
            avatarShape = AboutAvatarShape.COOKIE_9_SIDED,
            favoriteSongVideoId = "Mh2JWGWvy_Y",
        )

    val otherContributors: List<AboutContributor> =
        listOf(
            AboutContributor(
                name = "Pipo Team",
                role = AboutContributorRole.PIPO_TEAM,
                githubHandle = "MuzikGroup",
                websiteUrl = "https://pipo.ai",
                avatarShape = AboutAvatarShape.COOKIE_4_SIDED,
            ),
        )

    val all: List<AboutContributor> = listOf(leadDeveloper) + otherContributors
}

enum class AboutCommunityLinkKind {
    DISCORD,
    TELEGRAM,
    REPOSITORY,
    LICENSE,
}

data class AboutCommunityLink(
    val kind: AboutCommunityLinkKind,
    val url: String,
)

val aboutCommunityLinks: List<AboutCommunityLink> =
    listOf(
        AboutCommunityLink(AboutCommunityLinkKind.DISCORD, "https://discord.com/invite/zrdbeRG2Mt"),
        AboutCommunityLink(AboutCommunityLinkKind.TELEGRAM, "https://t.me/Muzik_ke"),
        AboutCommunityLink(AboutCommunityLinkKind.REPOSITORY, "https://github.com/MuzikGroup/Muzik"),
    )

val aboutLicenseLink: AboutCommunityLink =
    AboutCommunityLink(AboutCommunityLinkKind.LICENSE, "https://github.com/MuzikGroup/Muzik/blob/main/LICENSE")

data class AboutScreenText(
    val title: String,
    val leadSection: String,
    val teamSection: String,
    val leadDeveloperRole: String,
    val pipoTeamRole: String,
    val discord: String,
    val telegram: String,
    val github: String,
    val license: String,
    val licenseDescription: String,
) {
    fun roleLabel(role: AboutContributorRole): String =
        when (role) {
            AboutContributorRole.LEAD_DEVELOPER -> leadDeveloperRole
            AboutContributorRole.PIPO_TEAM -> pipoTeamRole
        }

    fun communityLabel(kind: AboutCommunityLinkKind): String =
        when (kind) {
            AboutCommunityLinkKind.DISCORD -> discord
            AboutCommunityLinkKind.TELEGRAM -> telegram
            AboutCommunityLinkKind.REPOSITORY -> github
            AboutCommunityLinkKind.LICENSE -> license
        }
}

@Composable
internal fun CommunitySection(
    text: AboutScreenText,
    onOpenLink: (AboutCommunityLink) -> Unit,
) {
    PlayerMenuPrimaryActions(
        actions =
            aboutCommunityLinks.map { link ->
                PlayerMenuAction(
                    icon =
                        when (link.kind) {
                            AboutCommunityLinkKind.DISCORD -> MuzikIconsBrand.Discord
                            AboutCommunityLinkKind.TELEGRAM -> MuzikIconsBrand.Telegram
                            AboutCommunityLinkKind.REPOSITORY -> MuzikIconsBrand.Github
                            AboutCommunityLinkKind.LICENSE -> MuzikIcons.Outlined.Info
                        },
                    title = text.communityLabel(link.kind),
                    onClick = { onOpenLink(link) },
                )
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorInfoSheet(
    contributor: AboutContributor,
    colorScheme: ColorScheme,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val roleLabel =
        when (contributor.role) {
            AboutContributorRole.LEAD_DEVELOPER -> sharedStringResource(Res.string.credits_lead_developer)
            AboutContributorRole.PIPO_TEAM -> sharedStringResource(Res.string.credits_other_contributors)
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val avatarShape = contributorShape(contributor)
                    AsyncImage(
                        url = contributor.avatarUrl,
                        modifier = Modifier.size(72.dp).clip(avatarShape),
                        contentDescription = contributor.name,
                        shape = avatarShape,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contributor.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                contributor.websiteUrl?.let { url ->
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(url) },
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.tertiaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = MuzikIcons.Outlined.Language,
                                contentDescription = null,
                                tint = colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sharedStringResource(Res.string.open_website),
                                color = colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                contributor.githubUrl?.let { url ->
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(url) },
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = MuzikIconsBrand.Github,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sharedStringResource(Res.string.open_github_profile),
                                color = colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                contributor.sponsorUrl?.let { url ->
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(url) },
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (
                                url.contains("buymeacoffee.com", ignoreCase = true) ||
                                url.contains("buymesoda.com", ignoreCase = true)
                            ) {
                                Icon(
                                    imageVector = MuzikIconsBrand.BuyMeACoffee,
                                    contentDescription = null,
                                    tint = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = sharedStringResource(Res.string.sponsor_developer),
                                color = colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
