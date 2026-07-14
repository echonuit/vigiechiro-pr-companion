package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Empreintes;
import fr.univ_amu.iut.commun.model.FichierWav;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Acceptee;
import fr.univ_amu.iut.passage.model.VerdictIdentite.NiveauConfiance;
import fr.univ_amu.iut.passage.model.VerdictIdentite.Refusee;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/// Vérifie qu'un fichier candidat est bien celui que la base décrit (#1309), par **cascade de
/// preuves** : la meilleure disponible l'emporte, et le verdict dit laquelle a joué.
///
/// 1. **Empreinte** (#1299), si la séquence en porte une : identité au contenu près, [NiveauConfiance#CERTITUDE].
/// 2. **Structurelle** : nom du fichier, durée **réelle** confrontée à l'en-tête WAV, taille si
///    connue. Disponible pour **tous** les passages existants, sans migration.
/// 3. **Acoustique** ([AnalyseAcoustique]) : les cris des observations sont-ils dans le fichier ?
///    Preuve **indépendante** de la structurelle ; les deux concordantes valent une empreinte
///    ([NiveauConfiance#CERTITUDE], et c'est expliqué à l'utilisateur). Une séquence sans
///    observation n'est pas vérifiable ainsi, mais elle n'a **rien à corrompre** : structurelle
///    seule, [NiveauConfiance#FORTE].
/// 4. **Bruts** ([#verifierOriginal]) : quand l'utilisateur n'a gardé que ses originaux, leur
///    `sha256` intégral (posé à l'import depuis toujours) prouve l'original ; la redécoupe étant
///    déterministe au bit près (R11), les séquences re-produites héritent de cette identité.
///
/// Les seuils sont **permissifs** ([#TOLERANCE_DUREE_SECONDES], [#SEUIL_FRACTION_CRIS]) : un faux
/// négatif (refuser le bon fichier) coûte plus cher à l'utilisateur qu'un faux positif quand
/// plusieurs preuves concordent déjà. Le mode de défaillance réel est **systémique** (se tromper de
/// dossier), pas ponctuel : c'est à l'orchestration de la réactivation (#1302) d'échantillonner.
public class VerificationIdentiteAudio {

    /// Facteur d'expansion du pipeline (cf. `TransformationAudio`) : l'en-tête d'une séquence porte
    /// Fe/10, sa durée réelle est donc la durée « à l'écoute » divisée par 10 (#1051).
    private static final int FACTEUR_EXPANSION = 10;

    /// Tolérance sur la durée réelle (150 ms) : large devant la précision du découpage (une trame),
    /// étroite devant une redécoupe à d'autres paramètres (dernière tranche décalée de secondes).
    private static final double TOLERANCE_DUREE_SECONDES = 0.15;

    /// Fraction des cris attendus à retrouver pour déclarer l'acoustique concordante. Majoritaire
    /// mais permissif : un cri faible peut se perdre, la moitié qui manque ne se perd pas.
    private static final double SEUIL_FRACTION_CRIS = 0.5;

    /// Vérifie qu'un fichier candidat est bien la séquence d'écoute que la base décrit.
    ///
    /// @param sequence la séquence à rebrancher (ses colonnes V23 peuvent être `NULL`)
    /// @param candidat le fichier proposé par l'utilisateur
    /// @param crisAttendus les cris de ses observations (projection faite par l'appelant, liste
    ///     vide si la séquence n'en porte aucune)
    public VerdictIdentite verifierSequence(SequenceDEcoute sequence, Path candidat, List<CriAttendu> crisAttendus) {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(candidat, "candidat");
        Objects.requireNonNull(crisAttendus, "crisAttendus");
        if (!Files.isRegularFile(candidat)) {
            return new Refusee("Fichier introuvable : " + candidat + ".");
        }
        String nomCandidat = candidat.getFileName().toString();
        if (!nomCandidat.equals(sequence.nomFichier())) {
            return new Refusee("Nom différent : « " + nomCandidat + " » au lieu de « " + sequence.nomFichier()
                    + " » (autre tranche, autre nuit ?).");
        }
        VerdictIdentite parTaille = controlerTaille(sequence.tailleOctets(), candidat);
        if (parTaille != null) {
            return parTaille;
        }
        if (sequence.empreinte() != null) {
            return verdictParEmpreinte(sequence.empreinte(), candidat);
        }
        return verdictSansEmpreinte(sequence, candidat, crisAttendus);
    }

    /// Vérifie qu'un fichier candidat est bien l'enregistrement **original** que la base décrit
    /// (voie « recoupe depuis les bruts » : l'utilisateur n'a gardé que ses originaux).
    public VerdictIdentite verifierOriginal(EnregistrementOriginal original, Path candidat) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(candidat, "candidat");
        if (!Files.isRegularFile(candidat)) {
            return new Refusee("Fichier introuvable : " + candidat + ".");
        }
        String nomCandidat = candidat.getFileName().toString();
        if (!porteLeNom(original, nomCandidat)) {
            return new Refusee("Nom différent : « " + nomCandidat + " » au lieu de « " + original.nomFichier() + " ».");
        }
        VerdictIdentite parTaille = controlerTaille(original.tailleOctets(), candidat);
        if (parTaille != null) {
            return parTaille;
        }
        if (original.sha256() == null) {
            return new Acceptee(
                    NiveauConfiance.FORTE,
                    "Nom concordant ; pas de SHA-256 en base pour aller plus loin (import très ancien).");
        }
        return Empreintes.sha256Hex(candidat).equals(original.sha256())
                ? new Acceptee(
                        NiveauConfiance.CERTITUDE,
                        "SHA-256 intégral identique : l'original est prouvé, la redécoupe déterministe (R11)"
                                + " transmettra cette identité aux séquences re-produites.")
                : new Refusee("SHA-256 différent : même nom, mais ce n'est pas l'enregistrement d'origine.");
    }

    /// Le candidat porte-t-il le nom de cet original ? Deux formes sont admises (#1406), parce que
    /// l'utilisateur sauvegarde ce qu'il veut, pas ce qui nous arrange :
    ///
    /// - le **nom R6** tel qu'en base (`Car640380-2026-Pass1-A1-PaRec…wav`) : une copie du dossier
    ///   `bruts/` de l'espace de travail ;
    /// - le **nom d'origine de l'enregistreur** (`PaRec…wav`), non encore préfixé : une copie de la
    ///   carte SD. Le préfixe R6 est justement ce que l'import ajoute (`Renommeur`), et le nom R6 se
    ///   termine donc par `-` + ce nom-là.
    ///
    /// Ce contrôle ne **prouve** rien : c'est un filtre à bon marché. Le SHA-256 intégral, lui, tranche.
    private static boolean porteLeNom(EnregistrementOriginal original, String nomCandidat) {
        return nomCandidat.equals(original.nomFichier())
                || original.nomFichier().endsWith(Prefixe.TIRET + nomCandidat);
    }

    /// Taille connue en base et différente sur disque : refus immédiat (discriminant quasi gratuit,
    /// contrôlé avant toute lecture de contenu). `null` si le contrôle ne tranche pas.
    private VerdictIdentite controlerTaille(Long tailleAttendue, Path candidat) {
        if (tailleAttendue == null) {
            return null;
        }
        long tailleCandidat;
        try {
            tailleCandidat = Files.size(candidat);
        } catch (IOException e) {
            return new Refusee("Fichier illisible : " + candidat + ".");
        }
        return tailleCandidat == tailleAttendue
                ? null
                : new Refusee("Taille différente : " + tailleCandidat + " octets au lieu de " + tailleAttendue
                        + " (contenu forcément différent).");
    }

    private VerdictIdentite verdictParEmpreinte(String empreinteAttendue, Path candidat) {
        try {
            return Empreintes.empreinteCourte(candidat).equals(empreinteAttendue)
                    ? new Acceptee(NiveauConfiance.CERTITUDE, "Empreinte de contenu identique (#1299).")
                    : new Refusee("Empreinte de contenu différente : même nom, mais pas le même audio"
                            + " (redécoupe ou autre expansion ?).");
        } catch (IllegalStateException e) {
            return new Refusee("Fichier illisible : " + candidat + ".");
        }
    }

    /// Cascade sans empreinte : structurelle (durée réelle vs en-tête) puis acoustique (cris).
    private VerdictIdentite verdictSansEmpreinte(
            SequenceDEcoute sequence, Path candidat, List<CriAttendu> crisAttendus) {
        FichierWav wav;
        try {
            wav = FichierWav.lire(candidat);
        } catch (IOException e) {
            return new Refusee("WAV illisible (" + e.getMessage() + ") : " + candidat + ".");
        }
        double dureeReelle = wav.dureeSecondes() / FACTEUR_EXPANSION;
        if (sequence.dureeSecondes() != null
                && Math.abs(dureeReelle - sequence.dureeSecondes()) > TOLERANCE_DUREE_SECONDES) {
            return new Refusee(String.format(
                    "Durée réelle différente : %.2f s au lieu de %.2f s (redécoupe à d'autres paramètres ?).",
                    dureeReelle, sequence.dureeSecondes()));
        }
        if (crisAttendus.isEmpty()) {
            return new Acceptee(
                    NiveauConfiance.FORTE,
                    "Structurelle : nom et durée réelle concordants. Aucune observation à confronter"
                            + " (rien à corrompre).");
        }
        OptionalDouble fraction = AnalyseAcoustique.fractionCrisPresents(wav, crisAttendus);
        if (fraction.isEmpty()) {
            return new Acceptee(
                    NiveauConfiance.FORTE,
                    "Structurelle : nom et durée réelle concordants (vérification acoustique non applicable"
                            + " sur ce fichier).");
        }
        int pourcent = (int) Math.round(fraction.getAsDouble() * 100);
        return fraction.getAsDouble() >= SEUIL_FRACTION_CRIS
                ? new Acceptee(
                        NiveauConfiance.CERTITUDE,
                        "Structurelle + acoustique : " + pourcent + " % des cris attendus retrouvés aux instants"
                                + " et fréquences des observations. Deux preuves indépendantes concordantes"
                                + " valent une empreinte.")
                : new Refusee("Acoustique discordante : " + pourcent + " % seulement des cris attendus retrouvés."
                        + " Même nom et même durée, mais ce n'est probablement pas le bon audio.");
    }
}
