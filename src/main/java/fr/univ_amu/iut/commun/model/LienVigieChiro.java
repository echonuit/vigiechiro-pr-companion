package fr.univ_amu.iut.commun.model;

/// Correspondance entre une entité **locale** et un objet **VigieChiro** (table `vigiechiro_link`,
/// #728). Amorcée à la connexion depuis l'API (`GET /taxons/liste`, `GET /moi/sites`), elle relie nos
/// clés locales aux `objectid` MongoDB de la plateforme, prérequis du référentiel taxons (axe 2), de
/// l'import des sites (axe 3) et du dépôt (axe 4).
///
/// Entité générique, discriminée par [#entite] : la clé locale [#refLocale] est interprétée selon le
/// type (`code` du taxon pour [#ENTITE_TAXON], `id` technique du site en texte pour [#ENTITE_SITE]).
///
/// @param entite type d'entité rapprochée ([#ENTITE_TAXON] ou [#ENTITE_SITE])
/// @param refLocale clé locale (taxon `code`, ou `monitoring_site.id` en texte)
/// @param objectid identifiant VigieChiro (`_id` MongoDB, 24 caractères hexadécimaux)
/// @param verrouille état *verrouillé* de l'objet VigieChiro (dépôt possible), **spécifique aux sites**
///     (#718) : `null` pour un taxon ou tant qu'aucune synchro ne l'a renseigné
public record LienVigieChiro(String entite, String refLocale, String objectid, Boolean verrouille) {

    /// Discriminant d'un rapprochement de **taxon** (clé locale = `taxon.code`).
    public static final String ENTITE_TAXON = "taxon";

    /// Discriminant d'un rapprochement de **site** (clé locale = `monitoring_site.id` en texte).
    public static final String ENTITE_SITE = "site";

    /// Discriminant d'un rapprochement de **passage** (clé locale = `passage.id` en texte, `objectid` =
    /// `_id` de la participation VigieChiro). Posé au dépôt (#142) pour retrouver la participation d'un
    /// passage lors de l'import de ses résultats Tadarida (axe 4.2). `verrouille` non pertinent (`null`).
    public static final String ENTITE_PASSAGE = "passage";

    /// Correspondance sans état de verrouillage (`verrouille` = `null`) : cas des taxons et des liens
    /// posés avant l'axe 3. Laisse le code existant (constructeur à 3 arguments) inchangé.
    public LienVigieChiro(String entite, String refLocale, String objectid) {
        this(entite, refLocale, objectid, null);
    }
}
