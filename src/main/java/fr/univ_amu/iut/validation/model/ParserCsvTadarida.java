package fr.univ_amu.iut.validation.model;

import fr.univ_amu.iut.commun.model.LecteurCsv;
import fr.univ_amu.iut.commun.model.ModeValidation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Parseur d'un fichier de résultats Tadarida (parcours P7, étape E7 ; règle R17). Lit un CSV
/// d'observations (séparateur `;`) et le projette en [ResultatParseTadarida] : le
/// [FormatTadarida] détecté + une [LigneObservation] par ligne.
///
/// **Classe `model` pure** : aucune dépendance JavaFX ni SQL. La lecture brute du CSV est
/// déléguée à l'utilitaire commun [LecteurCsv] (gestion RFC 4180 des guillemets, `\n` et `\r\n`).
/// Le parseur ajoute par-dessus la **détection de format** et le
/// **mapping des colonnes Tadarida**.
///
/// ## Détection Brut vs Vu
///
/// Les deux fichiers ont la **même entête** et les mêmes colonnes ; ils ne diffèrent que par la
/// forme :
///
/// - **Brut** (`*-observations.csv`) : **tous** les champs sont guillemetés, l'entête commence
///   donc par `"nom du fichier"…`. Les colonnes observateur sont vides (`""`).
/// - **Vu** (`*-observations_Vu.csv`) : entête nu (`nom du fichier;…`), champs guillemetés
///   seulement si nécessaire. Réinjectable, il peut porter les décisions de l'observateur.
///
/// On détecte donc sur la **présence de guillemets autour de l'entête** (et non sur le suffixe
/// `_Vu` du nom de fichier, peu fiable).
///
/// ## Champ « vide »
///
/// Dans un Brut un champ vide est `""` (chaîne vide après déguillemetage). Dans certains Vu
/// réinjectés un champ vide a été ré-encodé en un guillemet littéral seul (`""""` dans le
/// fichier, qui se relit en un caractère `"`). Les deux représentations sont normalisées en
/// `null` (cf. [#estVide(String)]), pour que Brut et Vu se parsent en observations équivalentes.
///
/// ## Mapping des colonnes
///
/// Les colonnes sont repérées **par leur nom d'entête** (et non par position) pour rester robuste
/// à un éventuel réordonnancement. Colonnes attendues : `nom du fichier`, `temps_debut`,
/// `temps_fin`, `frequence_mediane`, `tadarida_taxon`, `tadarida_probabilite`,
/// `tadarida_taxon_autre`, `observateur_taxon`, `observateur_probabilite`. La colonne
/// `validation_mode` (R24) est facultative.
///
/// ## Probabilités non numériques
///
/// Les colonnes `*_probabilite` d'un _Vu réel ne contiennent pas toujours un flottant : l'observateur
/// peut y avoir saisi un **code de confiance textuel** (p. ex. `SUR` pour « sûr »). Une telle valeur
/// est tolérée et lue comme une **probabilité inconnue** (`null`) plutôt que de faire échouer tout
/// l'import (cf. [#probabilite(String)]). Cette tolérance est **limitée aux colonnes de probabilité** :
/// `temps_debut`, `temps_fin` et `frequence_mediane` restent en lecture stricte (une valeur malformée
/// y lève, plutôt que d'être avalée silencieusement).
public final class ParserCsvTadarida {

    static final String COL_NOM = "nom du fichier";
    static final String COL_DEBUT = "temps_debut";
    static final String COL_FIN = "temps_fin";
    static final String COL_FREQ = "frequence_mediane";
    static final String COL_TAXON_TADARIDA = "tadarida_taxon";
    static final String COL_PROB_TADARIDA = "tadarida_probabilite";
    static final String COL_TAXON_AUTRE = "tadarida_taxon_autre";
    static final String COL_TAXON_OBSERVATEUR = "observateur_taxon";
    static final String COL_PROB_OBSERVATEUR = "observateur_probabilite";
    static final String COL_MODE_VALIDATION = "validation_mode";

    private static final char BOM = '﻿';

    private final LecteurCsv lecteur = new LecteurCsv(); // séparateur ';'

    /// Détecte le [FormatTadarida] d'un contenu CSV : [FormatTadarida#BRUT] si l'entête est
    /// guillemetée (commence par `"`), [FormatTadarida#VU] sinon.
    public FormatTadarida detecterFormat(String contenu) {
        Objects.requireNonNull(contenu, "contenu");
        String debut = sansBom(contenu).stripLeading();
        return debut.startsWith("\"") ? FormatTadarida.BRUT : FormatTadarida.VU;
    }

    /// Variante fichier de [#detecterFormat(String)].
    public FormatTadarida detecterFormat(Path fichier) {
        return detecterFormat(lire(fichier));
    }

    /// Parse un contenu CSV Tadarida (Brut ou Vu) en [ResultatParseTadarida].
    public ResultatParseTadarida parser(String contenu) {
        Objects.requireNonNull(contenu, "contenu");
        String propre = sansBom(contenu);
        FormatTadarida format = detecterFormat(propre);
        List<List<String>> lignes = lecteur.lire(propre);
        if (lignes.isEmpty()) {
            throw new IllegalArgumentException("CSV Tadarida vide : aucune entête.");
        }
        Map<String, Integer> index = indexerColonnes(lignes.get(0));
        int colNom = exigerColonne(index, COL_NOM);
        int colTaxon = exigerColonne(index, COL_TAXON_TADARIDA);

        List<LigneObservation> observations = new ArrayList<>();
        for (int i = 1; i < lignes.size(); i++) {
            List<String> ligne = lignes.get(i);
            String nom = texte(cellule(ligne, colNom));
            if (nom == null) {
                continue; // ligne vide (saut final, ligne blanche) : pas une observation
            }
            observations.add(new LigneObservation(
                    nom,
                    nombre(cellule(ligne, index.get(COL_DEBUT))),
                    nombre(cellule(ligne, index.get(COL_FIN))),
                    entier(cellule(ligne, index.get(COL_FREQ))),
                    texte(cellule(ligne, colTaxon)),
                    probabilite(cellule(ligne, index.get(COL_PROB_TADARIDA))),
                    texte(cellule(ligne, index.get(COL_TAXON_AUTRE))),
                    texte(cellule(ligne, index.get(COL_TAXON_OBSERVATEUR))),
                    probabilite(cellule(ligne, index.get(COL_PROB_OBSERVATEUR))),
                    mode(cellule(ligne, index.get(COL_MODE_VALIDATION)))));
        }
        return new ResultatParseTadarida(format, observations);
    }

    /// Variante fichier de [#parser(String)].
    public ResultatParseTadarida parser(Path fichier) {
        return parser(lire(fichier));
    }

    /// Lit le texte brut du fichier (UTF-8). On a besoin du texte non parsé pour la détection de
    /// format (qui repose sur les guillemets, perdus une fois le CSV parsé par [LecteurCsv]).
    private static String lire(Path fichier) {
        Objects.requireNonNull(fichier, "fichier");
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture CSV Tadarida impossible : " + fichier, e);
        }
    }

    /// Indexe l'entête : nom de colonne (en minuscules, trimé) → position.
    private static Map<String, Integer> indexerColonnes(List<String> entete) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < entete.size(); i++) {
            String nom = entete.get(i);
            if (nom != null) {
                index.put(nom.trim().toLowerCase(Locale.ROOT), i);
            }
        }
        return index;
    }

    private static int exigerColonne(Map<String, Integer> index, String nom) {
        Integer position = index.get(nom);
        if (position == null) {
            throw new IllegalArgumentException(
                    "Colonne « " + nom + " » absente de l'entête Tadarida : " + index.keySet());
        }
        return position;
    }

    /// Cellule à la position `col` (ou `null` si la position est absente / hors borne).
    private static String cellule(List<String> ligne, Integer col) {
        if (col == null || col < 0 || col >= ligne.size()) {
            return null;
        }
        return ligne.get(col);
    }

    /// Un champ est « vide » s'il est `null`, blanc, ou réduit à un guillemet littéral seul (`"`) —
    /// l'encodage d'un champ vide rencontré dans certains exports `_Vu`.
    static boolean estVide(String champ) {
        if (champ == null) {
            return true;
        }
        String trim = champ.trim();
        return trim.isEmpty() || trim.equals("\"");
    }

    private static String texte(String champ) {
        return estVide(champ) ? null : champ.trim();
    }

    /// Lit un nombre **strict** (temps, …) : une valeur non vide non numérique lève — une donnée
    /// malformée hors colonnes de probabilité n'est pas silencieusement avalée.
    private static Double nombre(String champ) {
        return estVide(champ) ? null : Double.valueOf(champ.trim());
    }

    /// Lit un entier **strict** (fréquence médiane) : arrondi du nombre, lève si non numérique.
    private static Integer entier(String champ) {
        return estVide(champ) ? null : (int) Math.round(Double.parseDouble(champ.trim()));
    }

    /// Lit une **probabilité tolérante** (colonnes `*_probabilite`) : les fichiers _Vu réels y portent
    /// parfois un **code de confiance textuel** (p. ex. « SUR » pour « sûr », cf. ligne 2627 du dataset
    /// SAÉ) là où le Brut a un flottant. Une valeur non numérique est lue comme une **probabilité
    /// inconnue** (`null`) plutôt que de faire échouer tout l'import. La tolérance est volontairement
    /// **limitée à ces colonnes** : temps et fréquence restent stricts (cf. [#nombre(String)]).
    private static Double probabilite(String champ) {
        if (estVide(champ)) {
            return null;
        }
        try {
            return Double.valueOf(champ.trim());
        } catch (NumberFormatException confianceTextuelle) {
            return null;
        }
    }

    private static ModeValidation mode(String champ) {
        return estVide(champ) ? ModeValidation.NON_VALIDE : ModeValidation.parLibelle(champ.trim());
    }

    private static String sansBom(String contenu) {
        return (!contenu.isEmpty() && contenu.charAt(0) == BOM) ? contenu.substring(1) : contenu;
    }
}
