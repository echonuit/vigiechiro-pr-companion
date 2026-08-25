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
/// Une URL signée décide **où partent les données** : c'est elle qu'on télécharge, et c'est vers elle
/// qu'on téléverse les enregistrements d'une nuit. La passer telle quelle à `URI.create` revient à
/// faire confiance au serveur sur ce champ ; une API compromise ou mal configurée pourrait les
/// envoyer ailleurs, éventuellement en clair.
///
/// La forme de référence vient du miroir de l'API
/// (`vigiechiro-api/vigiechiro/resources/fichiers.py:188`), qui construit **toutes** les URL signées
/// avec un schéma codé en dur et un hôte sous-domaine de `s3.amazonaws.com`. Une URL d'une autre
/// forme ne vient pas du chemin nominal.
///
/// Le serveur sait aussi renvoyer autre chose : `DEV_FAKE_S3_URL` (`fichiers.py:125`) produit
/// n'importe quel hôte, et une liste figée rendrait l'application inutilisable contre une instance de
/// développement. D'où la propriété système [#PROPRIETE_HOTES], qui **remplace** la liste sans
/// l'ouvrir à tout, et un refus qui nomme l'hôte observé **et** la propriété : un changement
/// d'hébergement doit être une ligne à ajouter, pas un mur.
///
/// Le schéma n'est pas surchargeable : ouvrir un hôte n'ouvre pas le HTTP en clair.
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
