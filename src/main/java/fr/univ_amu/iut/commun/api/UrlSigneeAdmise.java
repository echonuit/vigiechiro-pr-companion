package fr.univ_amu.iut.commun.api;

import fr.univ_amu.iut.commun.model.CleDeReglage;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/// Décide si une **URL pré-signée** rendue par le serveur peut être suivie (#2734).
///
/// ## Pourquoi il faut la regarder
///
/// Une URL signée décide **où partent les données** : c'est elle qu'on télécharge, et c'est vers elle
/// qu'on téléverse les enregistrements d'une nuit. Elle était passée telle quelle à `URI.create`, en
/// lecture comme en écriture : le client faisait donc confiance au serveur sur ce champ précis. Une API
/// compromise ou mal configurée pouvait envoyer les données ailleurs, éventuellement en clair.
///
/// ## La forme de référence n'est pas devinée
///
/// Le miroir de l'API (`vigiechiro-api/vigiechiro/resources/fichiers.py:188`) construit **toutes** les
/// URL signées ainsi :
///
/// ```python
/// s3_url = 'https://{}.s3.amazonaws.com/{}'.format(AWS_S3_BUCKET, object_name)
/// ```
///
/// Le schéma y est **codé en dur**, et l'hôte est toujours un sous-domaine de `s3.amazonaws.com`. Une
/// URL d'une autre forme ne vient pas du chemin nominal.
///
/// ## L'échappatoire, et pourquoi elle existe
///
/// Le serveur sait aussi renvoyer autre chose : quand `DEV_FAKE_S3_URL` est configuré
/// (`fichiers.py:125`), l'URL signée devient cette valeur suivie du nom d'objet, donc n'importe quel
/// hôte. Une liste figée rendrait l'application inutilisable contre une instance de développement.
///
/// D'où la propriété système [#PROPRIETE_HOTES], qui **remplace** la liste (elle ne l'élargit pas à
/// tout), et un message de refus qui nomme l'hôte observé **et** la propriété : un changement
/// d'hébergement côté plateforme doit être une ligne à ajouter, pas un mur.
///
/// Le schéma, lui, n'est pas surchargeable. Ouvrir un hôte n'ouvre pas le HTTP en clair.
public final class UrlSigneeAdmise {

    /// Liste des hôtes admis, séparés par des virgules, en remplacement des hôtes par défaut.
    public static final String PROPRIETE_HOTES = CleDeReglage.S3_HOTES.propriete();

    /// Le domaine de stockage de la plateforme : l'hôte doit lui être **égal** ou en être un
    /// sous-domaine.
    private static final List<String> HOTES_PAR_DEFAUT = List.of("s3.amazonaws.com");

    private UrlSigneeAdmise() {}

    /// Le motif de refus de `url`, ou vide si elle peut être suivie.
    ///
    /// Rend un motif plutôt que de lever : l'appelant est un transport, qui traduit ce refus dans son
    /// propre vocabulaire (`ReponseApi.Refuse`) sans rien émettre.
    public static Optional<String> motifDeRefus(String url) {
        if (url == null || url.isBlank()) {
            return Optional.of("URL de stockage absente : rien à télécharger ni à déposer.");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException illisible) {
            return Optional.of("URL de stockage illisible, l'appel n'a pas été tenté : " + illisible.getReason());
        }
        String schema = uri.getScheme();
        if (!"https".equalsIgnoreCase(schema == null ? "" : schema)) {
            return Optional.of("URL de stockage refusée : elle n'est pas en https (" + schema
                    + "). Les enregistrements ne partent pas en clair.");
        }
        String hote = uri.getHost();
        if (hote == null) {
            return Optional.of("URL de stockage refusée : aucun hôte lisible.");
        }
        if (hotesAdmis().stream().noneMatch(admis -> correspond(hote, admis))) {
            return Optional.of("URL de stockage refusée : hôte inattendu « " + hote + " », alors que la"
                    + " plateforme sert " + String.join(", ", hotesAdmis()) + ". Si l'hébergement a changé, "
                    + CleDeReglage.S3_HOTES.commentRelever());
        }
        return Optional.empty();
    }

    /// `hote` est admis s'il **est** `admis` ou en est un sous-domaine.
    ///
    /// Le point de séparation est ce qui distingue `bucket.s3.amazonaws.com` (légitime) de
    /// `s3.amazonaws.com.pirate.net` (dont le vrai domaine est `pirate.net`) : un `endsWith` sans le
    /// point admettrait le second, et un `contains` admettrait n'importe quoi qui cite la marque.
    private static boolean correspond(String hote, String admis) {
        String normalise = hote.toLowerCase(Locale.ROOT);
        String attendu = admis.toLowerCase(Locale.ROOT);
        return normalise.equals(attendu) || normalise.endsWith("." + attendu);
    }

    private static List<String> hotesAdmis() {
        String surcharge = System.getProperty(PROPRIETE_HOTES);
        if (surcharge == null || surcharge.isBlank()) {
            return HOTES_PAR_DEFAUT;
        }
        return Arrays.stream(surcharge.split(","))
                .map(String::trim)
                .filter(hote -> !hote.isEmpty())
                .toList();
    }
}
