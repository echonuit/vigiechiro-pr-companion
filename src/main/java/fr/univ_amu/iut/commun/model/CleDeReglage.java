package fr.univ_amu.iut.commun.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/// Les bornes que l'utilisateur peut **relever lui-même**, et le nom sous lequel il les désigne
/// (#4075).
///
/// ## Pourquoi ce registre existe
///
/// Ces bornes se surchargeaient déjà, mais seulement par **propriété JVM** - et un produit installé
/// n'en accepte aucune : le lanceur jpackage passe ses arguments à `main`, pas à la machine virtuelle.
/// Trois refus conseillaient pourtant `-D…`, c'est-à-dire un geste que seul un dépôt cloné permet.
/// La seule issue chez l'utilisateur était de renoncer au fichier, ce que le doc-comment de
/// [PlafondLecture] voulait précisément éviter.
///
/// ⚠️ **Ce registre ne rend pas ces bornes « réglables ».** Il n'y a toujours pas d'entrée dans l'écran
/// Réglages, et cette décision-là tient : un naturaliste n'a pas à choisir une taille de corps de
/// réponse. Ce qui change est qu'une limite atteinte **nomme la porte de sortie**, et que cette porte
/// existe là où l'utilisateur se trouve.
///
/// ## Pourquoi un registre plutôt que des noms libres
///
/// L'option `--reglage` pose une propriété système : sans liste, elle en poserait **n'importe
/// laquelle**, y compris celles de la plateforme. La clé se cherche donc ici, et une clé inconnue est
/// refusée en nommant celles qui existent.
///
/// C'est aussi ce qui rend l'ensemble **descriptible** : les bornes vivaient éparpillées dans quatre
/// classes, et rien ne disait lesquelles étaient une surface publique.
public enum CleDeReglage {

    /// Plafond des fichiers texte lus depuis la carte SD (journal du capteur, relevé climatique).
    IMPORT_JOURNAL_MAX_OCTETS("import.journal.max-octets", "taille maximale d'un fichier texte de la carte, en octets"),

    /// Plafond du corps d'une réponse de la plateforme.
    RESEAU_CORPS_MAX_OCTETS("reseau.corps.max-octets", "taille maximale d'une réponse du serveur, en octets"),

    /// Nombre d'entrées admises dans une archive à extraire.
    IMPORT_ZIP_MAX_ENTREES("import.zip.max-entrees", "nombre de fichiers admis dans une archive"),

    /// Taille admise pour un seul fichier d'une archive.
    IMPORT_ZIP_MAX_OCTETS_PAR_ENTREE(
            "import.zip.max-octets-par-entree", "taille maximale d'un fichier d'archive, en octets"),

    /// Taille totale admise pour une archive.
    IMPORT_ZIP_MAX_OCTETS_TOTAL("import.zip.max-octets-total", "taille totale admise d'une archive, en octets"),

    /// Marge d'espace disque exigée avant d'extraire.
    IMPORT_ZIP_MARGE_DISQUE("import.zip.marge-disque-octets", "espace disque à laisser libre, en octets"),

    /// Hôtes admis pour une URL signée (téléchargement des sons déposés).
    S3_HOTES("s3.hotes", "hôtes admis pour un lien de téléchargement, séparés par des virgules");

    /// Le préfixe de toutes les propriétés du produit. L'utilisateur ne le tape pas : il désigne la
    /// borne par son nom court, et c'est le registre qui compose la propriété.
    public static final String PREFIXE = "vigiechiro.";

    private final String nom;
    private final String description;

    CleDeReglage(String nom, String description) {
        this.nom = nom;
        this.description = description;
    }

    /// Le nom court, celui que l'utilisateur tape après `--reglage`.
    public String nom() {
        return nom;
    }

    /// Ce que la borne limite, en une ligne, pour l'aide.
    public String description() {
        return description;
    }

    /// La propriété système correspondante.
    ///
    /// ⚠️ C'est une **méthode** et non une constante : une constante de compilation serait inlinée par
    /// javac chez ses appelants, et la valeur du registre cesserait d'être la source (ADR 3947).
    public String propriete() {
        return PREFIXE + nom;
    }

    /// La clé portant ce nom court, si elle existe.
    public static Optional<CleDeReglage> parNom(String nom) {
        return Arrays.stream(values()).filter(cle -> cle.nom.equals(nom)).findFirst();
    }

    /// Tous les noms courts, dans l'ordre de déclaration, pour un message de refus qui **nomme** ce
    /// qui existe au lieu de laisser chercher.
    public static String nomsAdmis() {
        return Arrays.stream(values()).map(CleDeReglage::nom).collect(Collectors.joining(", "));
    }

    /// La phrase qui dit comment relever cette borne, telle qu'elle s'écrit dans un refus.
    ///
    /// ⚠️ Elle est la **même** pour les deux surfaces, et c'est délibéré. La ligne de commande est le
    /// seul endroit où ces bornes se relèvent ; l'écran n'en offre pas, et lui inventer une consigne
    /// propre reviendrait à promettre un geste qui n'existe pas. Dire « en ligne de commande » à
    /// l'écran reste vrai et actionnable pour qui a un terminal - ce que l'ADR 3947 appelle un repli
    /// honnête plutôt qu'un texte exact et sans valeur.
    public String commentRelever() {
        return "Cette limite se relève en ligne de commande : vigiechiro --reglage " + nom + "=<valeur>.";
    }
}
