package fr.univ_amu.iut.commun.model;

/// Le **format** d'un numéro de carré, règle métier R1 : six chiffres, département en tête, zéro de
/// gauche compris pour les départements 01 à 09.
///
/// Deux sources rendent un numéro et **toutes deux l'amputent** de ce zéro : la grille du portail
/// (`GET /grille_stoc/cercle`, mesuré le 2026-08-26) et le référentiel embarqué (13 342 numéros à cinq
/// chiffres sur 137 479). La règle vit donc ici, une fois, plutôt que chez chacune - c'est ce que la
/// décision D10 voulait dire par « au point où le numéro entre dans l'application », et il y a deux
/// entrées.
///
/// Un numéro à cinq chiffres ne remplit pas le champ de saisie, n'ouvre pas la vérification et ne
/// désigne aucun site du catalogue : `GET /sites?q=40110` ne trouve rien là où `q=040110` trouve son
/// site.
public final class NumeroDeCarre {

    private static final int CHIFFRES = 6;

    private NumeroDeCarre() {}

    /// Le numéro sur six chiffres. Un numéro déjà assez long passe **tel quel** : ce n'est pas à cette
    /// règle d'inventer ce qu'il faudrait faire d'un numéro trop long.
    public static String surSixChiffres(String numero) {
        return "0".repeat(Math.max(0, CHIFFRES - numero.length())) + numero;
    }
}
