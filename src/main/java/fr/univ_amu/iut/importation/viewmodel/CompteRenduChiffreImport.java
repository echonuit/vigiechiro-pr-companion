package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Barre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Motif;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Teinte;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Ventilation;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.importation.model.LigneRapport;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.importation.model.StatutImportFichier;
import fr.univ_amu.iut.importation.model.VolumesImport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Traduit un import abouti en **compte rendu chiffré** (#2358), celui que rend
/// [fr.univ_amu.iut.commun.view.PanneauCompteRendu].
///
/// Distinct de [CompteRenduImport], qui produit la restitution **textuelle** (ADR 0028/0031) : ce sont
/// deux lectures du même import, l'une en phrases et en listes, l'autre en proportions. Elles coexistent
/// le temps que le chiffré prenne la place, et rien ne les fait diverger - toutes deux ne lisent que le
/// [RapportImport] et les volumes déjà produits, sans rien recalculer.
///
/// Purement dérivé : aucune donnée n'est inventée ici. Les quantités viennent du rapport, les volumes de
/// [VolumesImport] (#2358), les motifs des lignes rejetées.
public final class CompteRenduChiffreImport {

    private CompteRenduChiffreImport() {}

    /// Le compte rendu chiffré d'un import **mono-nuit**.
    ///
    /// @param resultat l'import abouti
    /// @param actions ce que l'écran propose de faire ensuite ; un compte rendu ne se termine pas sur
    ///     « Fermer », et c'est l'écran qui sait où mènent ses boutons
    public static CompteRenduChiffre de(ResultatImport resultat, List<Action> actions) {
        return de(resultat, actions, ContexteApresImport.AUCUN);
    }

    /// Variante qui reçoit en plus ce que le rapport ne sait pas : les avertissements d'inspection encore
    /// vrais et la participation créée (#1488).
    public static CompteRenduChiffre de(ResultatImport resultat, List<Action> actions, ContexteApresImport contexte) {
        return construire(
                titreMonoNuit(resultat),
                List.of(resultat.rapport()),
                resultat.volumes(),
                resultat.anomalies(),
                actions,
                contexte.avecParticipations(resultat.participationCreee() ? 1 : 0));
    }

    /// Le compte rendu chiffré d'un import **multi-nuits** : mêmes catégories, agrégées sur toutes les
    /// nuits. Rendre compte de la seule première tairait les rejets des autres, or c'est sur un import
    /// multi-nuits qu'il y en a le plus.
    public static CompteRenduChiffre de(ResultatImportMultiNuits resultat, List<Action> actions) {
        return de(resultat, actions, ContexteApresImport.AUCUN);
    }

    /// Variante multi-nuits recevant le contexte d'après import (#1488). Les participations sont comptées
    /// **nuit par nuit** : sur une carte multi-nuits, certaines peuvent être publiées et d'autres non.
    public static CompteRenduChiffre de(
            ResultatImportMultiNuits resultat, List<Action> actions, ContexteApresImport contexte) {
        return construire(
                titreMultiNuits(resultat),
                resultat.parNuit().stream().map(ResultatImport::rapport).toList(),
                resultat.volumes(),
                resultat.parNuit().stream()
                        .flatMap(nuit -> nuit.anomalies().stream())
                        .toList(),
                actions,
                contexte.avecParticipations((int) resultat.parNuit().stream()
                        .filter(ResultatImport::participationCreee)
                        .count()));
    }

    private static CompteRenduChiffre construire(
            String titre,
            List<RapportImport> rapports,
            VolumesImport volumes,
            List<String> anomalies,
            List<Action> actions,
            ContexteApresImport contexte) {
        List<LigneRapport> lignes =
                rapports.stream().flatMap(rapport -> rapport.lignes().stream()).toList();
        long importes = compte(lignes, StatutImportFichier.IMPORTE);
        long rejetes = compte(lignes, StatutImportFichier.REJETE);
        return new CompteRenduChiffre(
                titre,
                resultat(importes, lignes.size()),
                severite(rapports, rejetes, anomalies),
                barresDeVolume(volumes),
                ventilation(lignes),
                motifs(rapports, lignes),
                avertissements(rapports, anomalies, contexte),
                actions);
    }

    /// « 583 / 612 importés » quand tout n'est pas passé, « 584 importés » quand si : afficher
    /// « 584 / 584 » ferait chercher l'écart qui n'existe pas.
    private static String resultat(long importes, int total) {
        return importes == total ? importes + " importés" : importes + " / " + total + " importés";
    }

    /// La sévérité du verdict. Un rejet n'est pas une erreur d'import - l'import a abouti, il est
    /// **résilient** (#155) - mais c'est un avertissement : des fichiers de la carte ne sont pas en base.
    /// Un doublon de nuit et une anomalie du journal du capteur pèsent pareil.
    private static Severite severite(List<RapportImport> rapports, long rejetes, List<String> anomalies) {
        boolean aDouter =
                rejetes > 0 || !anomalies.isEmpty() || rapports.stream().anyMatch(RapportImport::aDoublonDeNuit);
        return aDouter ? Severite.AVERTISSEMENT : Severite.SUCCES;
    }

    /// Ventilation **exhaustive** des fichiers de la source : chaque fichier est dans exactement un
    /// statut, donc la somme des segments fait le total, sans « autres » silencieux.
    private static Ventilation ventilation(List<LigneRapport> lignes) {
        if (lignes.isEmpty()) {
            return Ventilation.aucune();
        }
        List<Segment> segments = new ArrayList<>();
        ajouterSiPresent(segments, "Importés", compte(lignes, StatutImportFichier.IMPORTE), Teinte.RETENU);
        ajouterSiPresent(segments, "Ignorés", compte(lignes, StatutImportFichier.IGNORE), Teinte.ECARTE);
        ajouterSiPresent(segments, "Rejetés", compte(lignes, StatutImportFichier.REJETE), Teinte.REFUSE);
        return new Ventilation("Devenir des " + lignes.size() + " fichiers de la source", lignes.size(), segments);
    }

    /// Un segment ne se déclare que s'il a une quantité : un « 0 ignoré » en légende est du bruit, et un
    /// segment de largeur nulle dans la barre n'apprend rien.
    private static void ajouterSiPresent(List<Segment> segments, String libelle, long quantite, Teinte teinte) {
        if (quantite > 0) {
            segments.add(new Segment(libelle, quantite, String.valueOf(quantite), teinte));
        }
    }

    /// Les deux barres de volume, à échelle commune : ce que la carte a donné, ce que le disque a pris.
    /// Vides si rien n'a été mesuré - deux barres à zéro se liraient comme un import stérile.
    private static List<Barre> barresDeVolume(VolumesImport volumes) {
        if (volumes.estVide()) {
            return List.of();
        }
        List<Segment> ecrit = new ArrayList<>();
        if (volumes.octetsBruts() > 0) {
            ecrit.add(segmentDeVolume("bruts conservés", volumes.octetsBruts(), Teinte.PRINCIPALE));
        }
        ecrit.add(segmentDeVolume("séquences", volumes.octetsSequences(), Teinte.SECONDAIRE));
        return List.of(
                Barre.unique("Lu sur la carte", segmentDeVolume("lu", volumes.octetsLus(), Teinte.REFERENCE)),
                new Barre("Écrit sur le disque", ecrit));
    }

    private static Segment segmentDeVolume(String libelle, long octets, Teinte teinte) {
        return new Segment(libelle, octets, Formats.octetsLisibles(octets), teinte);
    }

    /// Les motifs de rejet, un par **raison**, chacun portant la liste de ses fichiers.
    ///
    /// ## Pourquoi la raison est retaillée avant de grouper
    ///
    /// La raison produite par le moteur **finit par le nom du fichier** (« Original illisible (…) :
    /// PaRec…_203922.wav ») : grouper sur elle telle quelle donnerait un motif par fichier, c'est-à-dire
    /// aucun groupe. Le nom est retiré de la raison - il est déjà le **sujet** - et sert de clé de
    /// regroupement.
    ///
    /// La raison reste un **texte d'exception**, ce que #2358 interdit d'afficher **seul** : ici elle
    /// accompagne un motif dénombré, une ventilation et une liste de fichiers nommés. La classer en
    /// causes typées (« en-tête WAV illisible », « fréquence inattendue ») demande de typer les rejets
    /// dans le moteur : c'est la substance de #2076, pas de cette issue.
    private static List<Motif> motifs(List<RapportImport> rapports, List<LigneRapport> lignes) {
        List<Motif> motifs = new ArrayList<>(motifsDeRejet(lignes));
        motifDesDoublons(rapports).ifPresent(motifs::add);
        return motifs;
    }

    private static List<Motif> motifsDeRejet(List<LigneRapport> lignes) {
        List<LigneRapport> rejets = lignes.stream()
                .filter(ligne -> ligne.statut() == StatutImportFichier.REJETE)
                .toList();
        // « fichier(s) », et non « fichiers » : le panneau compose le libellé APRÈS un effectif, et un motif
        // à un seul fichier donnait « 1 fichiers : … ». La marque « (s) » est la réponse déjà en usage dans
        // l'application (« %d fichier(s) non pertinent(s) ignoré(s) », « N séquence(s) »), vue à la capture.
        return Motif.grouperParCause(
                rejets,
                CompteRenduChiffreImport::raisonSansNomDeFichier,
                cause -> "fichier(s) : " + cause,
                LigneRapport::nomFichier);
    }

    /// Les passages **déjà présents** pour cette nuit (#214/#147), en motif dépliable.
    ///
    /// Ils n'y sont pas comme une cause de rejet mais pour la même raison : ce sont des sujets nommés que
    /// l'avertissement dénombre sans pouvoir les lister. Les verser dans le texte de l'avertissement les
    /// rendrait de longueur non bornée dans un encart d'une ligne, ce que l'ADR 0031 a précisément soldé ;
    /// en motif, ils se comptent en pied et s'ouvrent d'un geste.
    ///
    /// Le libellé vient de [AvertissementsInspection#libelle] : la même donnée était déjà mise en forme
    /// deux fois, avant l'import et après (#2050), il n'y aura pas de troisième rédaction.
    private static Optional<Motif> motifDesDoublons(List<RapportImport> rapports) {
        List<String> passages = rapports.stream()
                .flatMap(rapport -> rapport.doublonsDeNuit().stream())
                .map(AvertissementsInspection::libelle)
                .toList();
        return passages.isEmpty()
                ? Optional.empty()
                : Optional.of(new Motif("passage(s) déjà présent(s) pour cette nuit", passages));
    }

    /// Retire du message le suffixe « : nomDuFichier » que le moteur y appose. Sans correspondance, la
    /// raison est rendue telle quelle : mieux vaut un motif verbeux qu'un motif amputé au mauvais endroit.
    private static String raisonSansNomDeFichier(LigneRapport ligne) {
        String suffixe = " : " + ligne.nomFichier();
        String detail = ligne.detail();
        String raison = detail.endsWith(suffixe) ? detail.substring(0, detail.length() - suffixe.length()) : detail;
        return raison.isBlank() ? "raison non précisée" : raison;
    }

    /// Ce qui reste vrai après l'import et qu'aucun chiffre ne porte : le doublon de nuit assumé, les
    /// anomalies relevées au journal du capteur (R19).
    private static List<Avertissement> avertissements(
            List<RapportImport> rapports, List<String> anomalies, ContexteApresImport contexte) {
        List<Avertissement> avertissements = new ArrayList<>(contexte.avertissementsEncoreVrais().stream()
                .map(Avertissement::de)
                .toList());
        long doublons = rapports.stream()
                .flatMap(rapport -> rapport.doublonsDeNuit().stream())
                .count();
        if (doublons > 0) {
            avertissements.add(
                    Avertissement.de(
                            "Cette nuit était déjà importée : " + doublons
                                    + " passage(s) existant(s) pour la même série et la même date. Le passage créé en est un doublon assumé."));
        }
        anomalies.stream().map(Avertissement::de).forEach(avertissements::add);
        // L'écriture distante se dit (#1488) : elle porte les données de l'utilisateur sur un serveur, et
        // ne s'annonçait nulle part. Elle est dite au registre du SUCCÈS, pas de l'alerte : c'est un fait
        // accompli et voulu, non un problème - la sévérité par mention (#2358) permet enfin de le dire.
        // #3473 : et ce qu'il RESTE à y faire. Annoncer une création, c'est annoncer un fait accompli,
        // ce qui se lit « c'est fait » - or la fiche web attend encore des informations que Companion
        // ne connaît pas (météo saisie, matériel, commentaires). L'utilisateur qui a produit ce retour
        // demandait exactement cette phrase-là : « pensez à remplir les informations complémentaires ».
        //
        // ⚠️ Cette suite n'a pu être ajoutée qu'APRÈS #3448. Ce message s'affichait auparavant alors
        // qu'aucune participation n'avait été créée : l'enrichir l'aurait rendu plus convaincant, pas
        // plus vrai.
        if (contexte.participations() == 1) {
            avertissements.add(Avertissement.succes(
                    "Participation créée sur Vigie-Chiro : la nuit y est déclarée, le dépôt la réutilisera."
                            + " Pensez à la compléter sur le portail (météo, matériel, commentaires)."));
        } else if (contexte.participations() > 1) {
            avertissements.add(Avertissement.succes(contexte.participations()
                    + " participations créées sur Vigie-Chiro : les nuits y sont déclarées, le dépôt"
                    + " les réutilisera. Pensez à les compléter sur le portail (météo, matériel,"
                    + " commentaires)."));
        }
        return avertissements;
    }

    /// Ce que le compte rendu sait de l'import **au-delà du rapport** : les avertissements d'inspection
    /// encore vrais (#1488), que seul l'écran a vus, et le nombre de participations créées, que seul le
    /// service sait. Regroupés en un type valeur (doctrine de l'EPIC #2483) plutôt qu'ajoutés en
    /// paramètres voisins d'une signature déjà longue.
    ///
    /// @param avertissementsEncoreVrais cf. [AvertissementsInspection#encoreVraisApresImport]
    /// @param participations nombre de nuits pour lesquelles une participation a été créée
    public record ContexteApresImport(List<String> avertissementsEncoreVrais, int participations) {

        /// Rien à ajouter : import hors écran (outil de capture, test) et inspection sans réserve.
        public static final ContexteApresImport AUCUN = new ContexteApresImport(List.of(), 0);

        public ContexteApresImport {
            avertissementsEncoreVrais = List.copyOf(avertissementsEncoreVrais);
        }

        /// Le même contexte, avec le nombre de participations relevé sur le résultat. L'écran fournit les
        /// avertissements - il est le seul à avoir vu l'inspection - et le résultat porte les
        /// participations : chacun renseigne ce qu'il sait, personne ne devine la part de l'autre.
        ContexteApresImport avecParticipations(int combien) {
            return new ContexteApresImport(avertissementsEncoreVrais, combien);
        }
    }

    private static long compte(List<LigneRapport> lignes, StatutImportFichier statut) {
        return lignes.stream().filter(ligne -> ligne.statut() == statut).count();
    }

    /// Titre neutre, quand l'import n'a pas d'agrégat à nommer.
    private static final String TITRE_NU = "Import terminé";

    /// « Import terminé - nuit du 2026-04-22, carré 640380 · A1 ». Le carré et le point viennent du
    /// **nom du dossier de session** ; renommé à la main, le titre se réduit à la date plutôt que
    /// d'inventer un rattachement.
    ///
    /// L'agrégat peut manquer : [ResultatImport] admet un passage et une session nuls, ce dont usent les
    /// outils de capture, qui n'ont besoin que du rapport. Le titre se réduit alors, il ne casse pas.
    private static String titreMonoNuit(ResultatImport resultat) {
        if (resultat.passage() == null) {
            return TITRE_NU;
        }
        String date = TITRE_NU + " - nuit du " + resultat.passage().dateEnregistrement();
        if (resultat.session() == null) {
            return date;
        }
        return resultat.session()
                .prefixe()
                .map(prefixe -> date + ", carré " + prefixe.carre() + " · " + prefixe.codePoint())
                .orElse(date);
    }

    /// « Import terminé - 3 nuits, du 2026-04-22 au 2026-04-24 ». La plage dit d'un coup ce que couvre
    /// l'import, là où le seul nombre de passages laisse chercher lesquels.
    private static String titreMultiNuits(ResultatImportMultiNuits resultat) {
        List<ResultatImport> nuits = resultat.parNuit();
        if (nuits.isEmpty()) {
            return TITRE_NU;
        }
        if (nuits.size() == 1) {
            return titreMonoNuit(nuits.getFirst());
        }
        String premiere = nuits.getFirst().passage().dateEnregistrement();
        String derniere = nuits.getLast().passage().dateEnregistrement();
        return TITRE_NU + " - " + nuits.size() + " nuits, du " + premiere + " au " + derniere;
    }
}
