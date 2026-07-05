package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

/// Catalogue des **critères de filtrage** de la table audio (patron « à la Notion »). Chaque critère est
/// une entrée du menu « + Filtre » qui s'ajoute comme puce. Pour l'instant : **Statut**, **Groupe**
/// taxonomique et **Espèce** (taxon) ; les suivants — références, proba — s'ajouteront ici, un par PR.
final class CriteresAudio {

    /// Groupe des chauves-souris (`taxonomic_group.name`, cf. #507) : sélection par défaut du critère
    /// Groupe, car isoler les chiroptères est le levier n°1 de la revue (#471).
    private static final String GROUPE_CHIROPTERES = "Chiroptères";

    private CriteresAudio() {}

    /// Critère **Statut de revue** : éditeur = liste déroulante (À revoir / Validée / Corrigée) dans la
    /// puce ; par défaut **À revoir** (le plus utile pour la revue), appliqué dès l'ajout.
    static CritereFiltre statut() {
        return new CritereFiltre() {
            @Override
            public String nom() {
                return "statut";
            }

            @Override
            public String libelle() {
                return "Statut";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                ComboBox<StatutObservation> choix = new ComboBox<>();
                choix.getItems().setAll(StatutObservation.values());
                choix.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(StatutObservation statut) {
                        return statut == null ? "" : FormatLigneAudio.libelleStatut(statut);
                    }

                    @Override
                    public StatutObservation fromString(String libelle) {
                        return null; // liste non éditable
                    }
                });
                choix.valueProperty()
                        .addListener((obs, avant, statut) ->
                                applique.accept(statut == null ? ligne -> true : ligne -> ligne.statut() == statut));
                choix.setValue(StatutObservation.NON_TOUCHEE); // déclenche l'application initiale (À revoir)
                return choix;
            }
        };
    }

    /// Critère **Groupe taxonomique** : éditeur = liste déroulante des groupes **présents dans les lignes
    /// courantes** (dérivés de `lignesCourantes`, distincts et triés : Chiroptères / Oiseaux / Orthoptères
    /// et cigales…). Sélectionner « Chiroptères » revient à « chauves-souris uniquement » (#471), mais tout
    /// autre groupe est accessible. Par défaut **Chiroptères** s'il est présent (levier n°1 de la revue),
    /// sinon le premier groupe ; l'application est déclenchée dès l'ajout de la puce.
    static CritereFiltre groupe(Supplier<? extends List<LigneObservationAudio>> lignesCourantes) {
        return new CritereFiltre() {
            @Override
            public String nom() {
                return "groupe";
            }

            @Override
            public String libelle() {
                return "Groupe";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                ComboBox<String> choix = new ComboBox<>();
                choix.getItems().setAll(groupesPresents(lignesCourantes.get()));
                choix.valueProperty()
                        .addListener((obs, avant, groupe) -> applique.accept(
                                groupe == null ? ligne -> true : ligne -> groupe.equals(ligne.groupe())));
                choix.setValue(defaut(choix.getItems())); // déclenche l'application initiale
                return choix;
            }
        };
    }

    /// Groupes taxon parents présents dans `lignes` : non nuls, **distincts** et **triés** (source stable
    /// pour la liste déroulante du critère Groupe).
    private static List<String> groupesPresents(List<LigneObservationAudio> lignes) {
        return lignes.stream()
                .map(LigneObservationAudio::groupe)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /// Groupe présélectionné du critère Groupe : **Chiroptères** s'il figure parmi les groupes présents
    /// (cas d'usage principal en un clic), sinon le premier groupe disponible, ou `null` si aucun.
    private static String defaut(List<String> groupes) {
        if (groupes.contains(GROUPE_CHIROPTERES)) {
            return GROUPE_CHIROPTERES;
        }
        return groupes.isEmpty() ? null : groupes.get(0);
    }

    /// Critère **Taxon (espèce)** : éditeur = liste déroulante des espèces **présentes dans les lignes
    /// courantes**, chacune identifiée par son **taxon retenu** (`COALESCE(observateur, tadarida)`) et
    /// affichée par son nom vernaculaire (à défaut le code). Aucune présélection : la puce n'ajoutée ne
    /// filtre rien tant qu'une espèce n'est pas choisie (#472), pour ne pas masquer arbitrairement la table.
    static CritereFiltre taxon(Supplier<? extends List<LigneObservationAudio>> lignesCourantes) {
        return new CritereFiltre() {
            @Override
            public String nom() {
                return "taxon";
            }

            @Override
            public String libelle() {
                return "Espèce";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                ComboBox<EspecePresente> choix = new ComboBox<>();
                choix.getItems().setAll(especesPresentes(lignesCourantes.get()));
                choix.setPromptText("Choisir une espèce");
                choix.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(EspecePresente espece) {
                        return espece == null ? "" : espece.libelle();
                    }

                    @Override
                    public EspecePresente fromString(String libelle) {
                        return null; // liste non éditable
                    }
                });
                choix.valueProperty()
                        .addListener((obs, avant, espece) -> applique.accept(
                                espece == null
                                        ? ligne -> true
                                        : ligne -> espece.code().equals(codeRetenu(ligne))));
                return choix; // pas de présélection : filtre inactif tant qu'aucune espèce n'est choisie
            }
        };
    }

    /// Espèces présentes dans `lignes`, une par **taxon retenu**, **distinctes** et triées par libellé
    /// (source stable de la liste déroulante du critère Espèce).
    private static List<EspecePresente> especesPresentes(List<LigneObservationAudio> lignes) {
        return lignes.stream()
                .map(ligne -> new EspecePresente(codeRetenu(ligne), libelleEspece(ligne)))
                .distinct()
                .sorted(Comparator.comparing(EspecePresente::libelle))
                .toList();
    }

    /// Code du **taxon retenu** d'une ligne : celui de l'observateur s'il a tranché, sinon la proposition
    /// Tadarida (`COALESCE(observateur, tadarida)`, jamais nul car Tadarida est toujours présent).
    private static String codeRetenu(LigneObservationAudio ligne) {
        return ligne.taxonObservateur() != null ? ligne.taxonObservateur() : ligne.taxonTadarida();
    }

    /// Libellé d'une espèce : son nom vernaculaire (`nomEspece`, projeté du taxon retenu), ou le code
    /// retenu à défaut (souche hors référentiel sans vernaculaire).
    private static String libelleEspece(LigneObservationAudio ligne) {
        String vernaculaire = ligne.nomEspece();
        return vernaculaire != null && !vernaculaire.isBlank() ? vernaculaire : codeRetenu(ligne);
    }

    /// Une espèce présente dans le jeu : `code` = taxon retenu (clé du prédicat), `libelle` = affichage.
    private record EspecePresente(String code, String libelle) {}
}
