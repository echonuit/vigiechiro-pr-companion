package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.FichierWav;
import java.util.List;
import java.util.OptionalDouble;

/// Vérification **acoustique** légère (#1309) : les cris attendus (instants et fréquences médianes
/// des observations) sont-ils bien présents dans un fichier candidat ? Il ne s'agit pas de rejouer
/// Tadarida, seulement de mesurer une **présence d'énergie** à l'instant et dans la bande attendus.
///
/// Implémentée par **filtre de Goertzel** (puissance à une fréquence donnée sur une fenêtre), en
/// `java.base` pur : le paquet DSP d'`audio-view` est délibérément non exporté par son module
/// (boîte noire), et un spectrogramme complet serait disproportionné pour un test de présence.
///
/// Un cri est **présent** si son énergie **de pointe**, à sa fréquence médiane, dépasse nettement
/// ([#RAPPORT_PRESENCE]) le plancher des fenêtres témoin prélevées ailleurs dans le fichier (médiane
/// robuste). On cherche la **pointe** sur une fenêtre courte glissée dans la fenêtre de l'observation
/// (#1687), et non l'énergie moyenne de toute cette fenêtre : un cri de chauve-souris ne dure que
/// quelques ms, une observation peut couvrir plusieurs secondes, et la moyenne le **diluait** au plancher
/// (faux négatifs). Les seuils restent volontairement **permissifs** : on cherche à écarter un fichier
/// manifestement autre, pas à réidentifier l'espèce.
final class AnalyseAcoustique {

    /// Facteur d'expansion de temps du pipeline Vigie-Chiro (cf. `TransformationAudio`) : l'en-tête
    /// des séquences porte Fe/10, les instants et fréquences des observations sont **réels**.
    private static final int FACTEUR_EXPANSION = 10;

    /// Rapport d'énergie exigé entre la fenêtre du cri et la médiane des fenêtres témoin (+6 dB) :
    /// assez haut pour qu'un bruit blanc étranger ne le franchisse pas par hasard, assez bas pour
    /// tolérer un cri faible au-dessus du plancher.
    private static final double RAPPORT_PRESENCE = 4.0;

    /// Nombre de fenêtres témoin réparties uniformément dans le fichier (médiane robuste : un cri
    /// réel tombant dans une témoin ne fausse pas le plancher).
    private static final int NB_FENETRES_TEMOIN = 8;

    /// En dessous de cette longueur de fenêtre (échantillons), la mesure n'a pas de sens : le cri
    /// est ignoré (ni présent ni absent).
    private static final int LONGUEUR_MIN_FENETRE = 64;

    /// Durée d'analyse d'un cri, en secondes **réelles** (#1687). Un cri de chauve-souris ne dure que
    /// quelques ms, mais la fenêtre `[start, end]` d'une observation peut couvrir **plusieurs secondes** :
    /// mesurer l'énergie sur toute la fenêtre **diluait** le cri jusqu'au plancher du bruit (faux
    /// négatifs). On cherche donc l'énergie **de pointe** sur une fenêtre courte glissée dans `[start,
    /// end]`. 10 ms : assez long pour porter un cri, assez court pour ne pas le noyer.
    private static final double DUREE_FENETRE_CRI_SECONDES = 0.010;

    private AnalyseAcoustique() {}

    /// Fraction des cris attendus retrouvés dans `wav` (0..1), ou vide si rien n'est mesurable
    /// (format non géré, cris tous hors fenêtre ou trop courts) : l'appelant retombe alors sur la
    /// preuve structurelle seule.
    static OptionalDouble fractionCrisPresents(FichierWav wav, List<CriAttendu> cris) {
        if (wav.bitsParEchantillon() != 16 || wav.nombreCanaux() != 1) {
            return OptionalDouble.empty();
        }
        double[] echantillons = echantillons(wav);
        double frequenceReelle = (double) wav.frequenceEchantillonnageHz() * FACTEUR_EXPANSION;
        int fenetreCourte =
                Math.max(LONGUEUR_MIN_FENETRE, (int) Math.round(DUREE_FENETRE_CRI_SECONDES * frequenceReelle));
        int testes = 0;
        int presents = 0;
        for (CriAttendu cri : cris) {
            int debut = (int) Math.round(cri.debutSecondes() * frequenceReelle);
            int fin = (int) Math.min(Math.round(cri.finSecondes() * frequenceReelle), echantillons.length);
            if (debut < 0 || fin - debut < LONGUEUR_MIN_FENETRE || cri.frequenceMedianeHz() >= frequenceReelle / 2) {
                continue;
            }
            testes++;
            if (criPresent(echantillons, debut, fin, cri.frequenceMedianeHz() / frequenceReelle, fenetreCourte)) {
                presents++;
            }
        }
        return testes == 0 ? OptionalDouble.empty() : OptionalDouble.of(presents / (double) testes);
    }

    /// Le cri est présent si son énergie **de pointe** (max sur une fenêtre courte glissée dans `[debut,
    /// fin)`, à sa fréquence) dépasse nettement le plancher : la médiane de fenêtres témoin de **même
    /// longueur courte** prélevées dans tout le fichier. Chercher la **pointe** et non la moyenne de toute
    /// la fenêtre évite de diluer un cri bref dans une longue fenêtre d'observation (#1687).
    private static boolean criPresent(
            double[] echantillons, int debut, int fin, double frequenceNormalisee, int fenetreCourte) {
        int longueur = Math.min(fenetreCourte, fin - debut);
        double energieCri = maxPuissance(echantillons, debut, fin, longueur, frequenceNormalisee);
        double[] temoins = new double[NB_FENETRES_TEMOIN];
        int pas = Math.max(1, (echantillons.length - longueur) / NB_FENETRES_TEMOIN);
        for (int i = 0; i < NB_FENETRES_TEMOIN; i++) {
            int depart = Math.min(i * pas, echantillons.length - longueur);
            temoins[i] = puissanceGoertzel(echantillons, depart, depart + longueur, frequenceNormalisee);
        }
        java.util.Arrays.sort(temoins);
        double plancher = Math.max(temoins[NB_FENETRES_TEMOIN / 2], Double.MIN_NORMAL);
        return energieCri > RAPPORT_PRESENCE * plancher;
    }

    /// Énergie **de pointe** à la fréquence donnée : le maximum de la puissance de Goertzel sur une fenêtre
    /// de `longueur` échantillons glissée (recouvrement 50 %) dans `[debut, fin)`. La fenêtre étant bornée
    /// par la longueur du cri, une observation brève retombe sur une unique fenêtre (comportement d'avant).
    private static double maxPuissance(
            double[] echantillons, int debut, int fin, int longueur, double frequenceNormalisee) {
        int pas = Math.max(1, longueur / 2);
        double max = 0;
        for (int depart = debut; depart + longueur <= fin; depart += pas) {
            max = Math.max(max, puissanceGoertzel(echantillons, depart, depart + longueur, frequenceNormalisee));
        }
        return max;
    }

    /// Puissance du signal à la fréquence normalisée `f/Fe` sur `[debut, fin)` (algorithme de
    /// Goertzel : l'équivalent d'un unique bin de FFT, sans calculer le spectre entier).
    private static double puissanceGoertzel(double[] echantillons, int debut, int fin, double frequenceNormalisee) {
        double coefficient = 2 * Math.cos(2 * Math.PI * frequenceNormalisee);
        double s1 = 0;
        double s2 = 0;
        for (int n = debut; n < fin; n++) {
            double s0 = echantillons[n] + coefficient * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        return s1 * s1 + s2 * s2 - coefficient * s1 * s2;
    }

    /// Échantillons 16 bits little-endian mono, normalisés en [-1, 1].
    private static double[] echantillons(FichierWav wav) {
        byte[] pcm = wav.donneesPcm();
        double[] echantillons = new double[pcm.length / 2];
        for (int i = 0; i < echantillons.length; i++) {
            int bas = pcm[2 * i] & 0xFF;
            int haut = pcm[2 * i + 1];
            echantillons[i] = ((haut << 8) | bas) / 32768.0;
        }
        return echantillons;
    }
}
