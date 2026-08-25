package fr.univ_amu.iut.lot.model;

import java.util.Arrays;

/// **Sous quelle forme une nuit part sur la plateforme** (#1997) : en archives ZIP, ou séquence par
/// séquence.
///
/// Le choix est **explicite**, et non déduit de la place disque comme il l'était. Le pipeline (#1995)
/// ne matérialise que deux archives à la fois, donc le ZIP est pratiquement toujours possible et un
/// repli automatique vers WAV ne se déclencherait plus jamais : sans ce choix explicite, la seule route
/// par laquelle l'IHM produisait un dépôt WAV disparaissait en silence.
///
/// En **ZIP**, la plateforme extrait l'archive puis la **détruit**, et ne remonte pas les WAV extraits
/// sur S3 (#1244). L'audio n'est donc pas récupérable côté serveur, et relancer le calcul de la
/// participation effacerait ses observations **sans pouvoir les recalculer** : c'est la raison d'être
/// du verrou `relanceBloquee`.
///
/// En **WAV**, chaque séquence déposée garde son `s3_id` et survit au traitement : l'audio reste
/// téléchargeable, la participation relançable.
///
/// Le défaut reste [#ARCHIVES_ZIP], comportement établi et le plus rapide, mais c'en est maintenant un
/// que l'on choisit.
public enum ModeDepot {

    /// Archives ZIP (défaut) : rapide, peu de requêtes. L'audio n'est **pas** récupérable côté serveur
    /// après traitement, et la participation ne pourra pas être relancée (#1244).
    ARCHIVES_ZIP("zip", "Archives ZIP (rapide)"),

    /// Séquences WAV une à une : plus lent (une requête par séquence), mais l'audio est **conservé** côté
    /// serveur et la participation reste relançable.
    SEQUENCES_WAV("wav", "Séquences WAV (audio conservé en ligne)");

    private final String valeur;
    private final String libelle;

    ModeDepot(String valeur, String libelle) {
        this.valeur = valeur;
        this.libelle = libelle;
    }

    /// Valeur persistée dans les réglages (stable : ne pas renommer, des bases la portent).
    public String valeur() {
        return valeur;
    }

    /// Libellé affiché dans la liste déroulante des réglages.
    public String libelle() {
        return libelle;
    }

    /// Le mode désigné par sa valeur persistée, ou [#ARCHIVES_ZIP] si elle est absente ou inconnue :
    /// un réglage corrompu ne doit pas empêcher de déposer.
    public static ModeDepot parValeur(String valeur) {
        return Arrays.stream(values())
                .filter(mode -> mode.valeur.equals(valeur))
                .findFirst()
                .orElse(ARCHIVES_ZIP);
    }
}
