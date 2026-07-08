package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/// Catalogue des **critères de filtrage** de la table audio (patron « à la Notion »). Chaque critère est
/// une entrée du menu « + Filtre » qui s'ajoute comme puce : **Statut**, **Groupe** taxonomique,
/// **Espèce** (taxon), **Références**, **Proba** (seuil de probabilité Tadarida) et **Heure** (plage horaire).
final class CriteresAudio {

    /// Groupe des chauves-souris (`taxonomic_group.name`, cf. #507) : sélection par défaut du critère
    /// Groupe, car isoler les chiroptères est le levier n°1 de la revue (#471).
    private static final String GROUPE_CHIROPTERES = "Chiroptères";

    /// Bornes de la plage **nuit** par défaut du critère Heure (21 h → 6 h, à cheval sur minuit) : écarte
    /// d'emblée les heures de jour, cas d'usage principal (#531).
    private static final int HEURE_DEBUT_NUIT = 21;

    private static final int HEURE_FIN_NUIT = 6;

    private CriteresAudio() {}

    /// Critère **Statut de revue** : éditeur = liste déroulante (À revoir / Validée / Corrigée) dans la
    /// puce ; par défaut **À revoir** (le plus utile pour la revue), appliqué dès l'ajout.
    static CritereFiltre<LigneObservationAudio> statut() {
        return new CritereFiltre<LigneObservationAudio>() {
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

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of(((StatutObservation) valeur).name());
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (!valeurs.isEmpty()) {
                    selectionnerParValeur(editeur, StatutObservation.valueOf(valeurs.get(0)));
                }
            }
        };
    }

    /// Critère **Groupe taxonomique** : éditeur = liste déroulante des groupes **présents dans les lignes
    /// courantes** (dérivés de `lignesCourantes`, distincts et triés : Chiroptères / Oiseaux / Orthoptères
    /// et cigales…). Sélectionner « Chiroptères » revient à « chauves-souris uniquement » (#471), mais tout
    /// autre groupe est accessible. Par défaut **Chiroptères** s'il est présent (levier n°1 de la revue),
    /// sinon le premier groupe ; l'application est déclenchée dès l'ajout de la puce.
    static CritereFiltre<LigneObservationAudio> groupe(
            Supplier<? extends List<LigneObservationAudio>> lignesCourantes) {
        return new CritereFiltre<LigneObservationAudio>() {
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

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of((String) valeur);
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (!valeurs.isEmpty()) {
                    selectionnerParValeur(editeur, valeurs.get(0));
                }
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

    /// Sélectionne dans une liste déroulante l'élément **égal** à `valeur` (ou vide la sélection s'il est
    /// absent : `indexOf` → -1), pour restaurer une valeur mémorisée **sans cast générique non vérifié**.
    private static void selectionnerParValeur(Node comboBox, Object valeur) {
        ComboBox<?> choix = (ComboBox<?>) comboBox;
        choix.getSelectionModel().select(choix.getItems().indexOf(valeur));
    }

    /// Critère **Taxon (espèce)** : éditeur = liste déroulante des espèces **présentes dans les lignes
    /// courantes**, chacune identifiée par son **taxon retenu** (`COALESCE(observateur, tadarida)`) et
    /// affichée par son nom vernaculaire (à défaut le code). Aucune présélection : la puce n'ajoutée ne
    /// filtre rien tant qu'une espèce n'est pas choisie (#472), pour ne pas masquer arbitrairement la table.
    static CritereFiltre<LigneObservationAudio> taxon(Supplier<? extends List<LigneObservationAudio>> lignesCourantes) {
        return new CritereFiltre<LigneObservationAudio>() {
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

            @Override
            public List<String> valeurCourante(Node editeur) {
                Object valeur = ((ComboBox<?>) editeur).getValue();
                return valeur == null ? List.of() : List.of(((EspecePresente) valeur).code());
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (valeurs.isEmpty()) {
                    return;
                }
                ComboBox<?> choix = (ComboBox<?>) editeur;
                for (int i = 0; i < choix.getItems().size(); i++) {
                    if (choix.getItems().get(i) instanceof EspecePresente espece
                            && espece.code().equals(valeurs.get(0))) {
                        choix.getSelectionModel().select(i);
                        return;
                    }
                }
            }
        };
    }

    /// Critère **Références seulement** (booléen) : ne garde que les observations archivées en référence
    /// (`is_reference`). Critère **sans éditeur** — la simple présence de la puce active le filtre (#473),
    /// son retrait le désactive. Libellé en texte (l'étoile ⭐ ne rend pas dans toutes les polices, cf.
    /// [CellulesAudio] ; l'indication visuelle reste la colonne-icône dorée de la table).
    static CritereFiltre<LigneObservationAudio> references() {
        return new CritereFiltre<LigneObservationAudio>() {
            @Override
            public String nom() {
                return "references";
            }

            @Override
            public String libelle() {
                return "Références";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                applique.accept(LigneObservationAudio::reference); // filtre actif dès l'ajout de la puce
                return null; // booléen : pas d'éditeur, la présence de la puce suffit
            }
        };
    }

    /// Critère **Seuil de probabilité** : éditeur = **curseur** (0 à 100 %) dans la puce ; garde les
    /// observations dont la probabilité Tadarida est **≥ au seuil** (isoler les détections les plus sûres).
    /// Les observations **sans probabilité** sont **toujours conservées** (elles n'ont pas de confiance
    /// comparable au seuil, on évite de perdre des lignes à revoir). Défaut **50 %**, appliqué dès l'ajout.
    static CritereFiltre<LigneObservationAudio> probabilite() {
        return new CritereFiltre<LigneObservationAudio>() {
            @Override
            public String nom() {
                return "proba";
            }

            @Override
            public String libelle() {
                return "Proba";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                Slider curseur = new Slider(0, 1, 0.5);
                curseur.setBlockIncrement(0.05);
                curseur.setPrefWidth(120);
                Label valeur = new Label();
                valeur.textProperty()
                        .bind(curseur.valueProperty().map(v -> "≥ " + FormatLigneAudio.probabilite(v.doubleValue())));
                curseur.valueProperty()
                        .addListener((obs, avant, seuil) -> applique.accept(auMoins(seuil.doubleValue())));
                applique.accept(auMoins(curseur.getValue())); // application initiale (défaut 50 %)
                return new HBox(6, curseur, valeur);
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                Slider curseur = (Slider) ((HBox) editeur).getChildren().get(0);
                return List.of(Double.toString(curseur.getValue()));
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (!valeurs.isEmpty()) {
                    ((Slider) ((HBox) editeur).getChildren().get(0)).setValue(Double.parseDouble(valeurs.get(0)));
                }
            }
        };
    }

    /// Prédicat du seuil de probabilité : garde une observation si sa probabilité Tadarida est **≥ `seuil`**,
    /// **ou** si elle n'en a pas (sans proba toujours conservée, cf. [#probabilite()]).
    private static Predicate<LigneObservationAudio> auMoins(double seuil) {
        return ligne -> ligne.probTadarida() == null || ligne.probTadarida() >= seuil;
    }

    /// Critère **Plage horaire**, avec le défaut fixe **nuit (21 h → 6 h)** (#531). Variante sans plage
    /// dynamique, utilisée là où la nuit réelle n'est pas connue (tests, sources multi-nuits).
    static CritereFiltre<LigneObservationAudio> heure() {
        return heure(Optional::empty);
    }

    /// Critère **Plage horaire** : deux listes déroulantes « de » / « à » (heures 0–23) ; garde les
    /// observations dont l'**heure de capture** tombe dans la plage. Gère le **passage à minuit** : si `de`
    /// > `à` (ex. 21 h → 6 h), la plage traverse minuit (`heure ≥ de` **ou** `heure ≤ à`). Les observations
    /// **sans heure** sont **toujours conservées** (comme le seuil de proba, on évite de perdre des lignes).
    ///
    /// Le **défaut** des bornes est fourni par `plageParDefaut`, évalué à l'ouverture de l'éditeur (#549) :
    /// sur un passage, la **nuit réelle** (coucher → lever du soleil) ; sinon le défaut fixe **21 h → 6 h**,
    /// qui écarte trop en été et trop peu en hiver mais reste un repli raisonnable.
    ///
    /// @param plageParDefaut source des bornes par défaut, évaluée quand la puce « Heure » est ajoutée
    static CritereFiltre<LigneObservationAudio> heure(Supplier<Optional<PlageNuit>> plageParDefaut) {
        return new CritereFiltre<LigneObservationAudio>() {
            @Override
            public String nom() {
                return "heure";
            }

            @Override
            public String libelle() {
                return "Heure";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                PlageNuit defaut = plageParDefaut.get().orElse(new PlageNuit(HEURE_DEBUT_NUIT, HEURE_FIN_NUIT));
                ComboBox<Integer> de = choixHeure();
                ComboBox<Integer> a = choixHeure();
                de.setValue(defaut.heureDebut());
                a.setValue(defaut.heureFin());
                de.valueProperty()
                        .addListener((obs, avant, apres) -> applique.accept(dansPlage(de.getValue(), a.getValue())));
                a.valueProperty()
                        .addListener((obs, avant, apres) -> applique.accept(dansPlage(de.getValue(), a.getValue())));
                applique.accept(dansPlage(de.getValue(), a.getValue())); // application initiale (nuit)
                return new HBox(6.0, new Label("de"), de, new Label("à"), a);
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                HBox conteneur = (HBox) editeur;
                int debut = (Integer) ((ComboBox<?>) conteneur.getChildren().get(1)).getValue();
                int fin = (Integer) ((ComboBox<?>) conteneur.getChildren().get(3)).getValue();
                return List.of(Integer.toString(debut), Integer.toString(fin));
            }

            @Override
            public void restaurerValeurs(Node editeur, List<String> valeurs) {
                if (valeurs.size() >= 2) {
                    HBox conteneur = (HBox) editeur;
                    selectionnerParValeur(conteneur.getChildren().get(1), Integer.valueOf(valeurs.get(0)));
                    selectionnerParValeur(conteneur.getChildren().get(3), Integer.valueOf(valeurs.get(1)));
                }
            }
        };
    }

    /// Liste déroulante des heures de la journée (0 h – 23 h), affichées « 21 h ».
    private static ComboBox<Integer> choixHeure() {
        ComboBox<Integer> choix = new ComboBox<>();
        choix.getItems().setAll(IntStream.range(0, 24).boxed().toList());
        choix.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer heure) {
                return heure == null ? "" : heure + " h";
            }

            @Override
            public Integer fromString(String texte) {
                return null; // liste non éditable
            }
        });
        return choix;
    }

    /// Prédicat de plage horaire sur l'**heure de capture** (0–23). Gère le passage à minuit (`de` > `à` →
    /// `heure ≥ de` ou `heure ≤ à`) ; une observation **sans heure** est toujours conservée (cf. [#heure()]).
    private static Predicate<LigneObservationAudio> dansPlage(int de, int a) {
        return ligne -> {
            if (ligne.heureCapture() == null) {
                return true;
            }
            int h = ligne.heureCapture().getHour();
            return de <= a ? (h >= de && h <= a) : (h >= de || h <= a);
        };
    }

    /// **Recherche texte** de la barre de filtres audio : vrai si un des champs cherchables d'une ligne
    /// contient l'aiguille (comparaison **insensible casse/accents**) : fichier, **espèce retenue** (taxon +
    /// vernaculaire observateur `nomEspece`, ou Tadarida à défaut) et commentaire. On inclut
    /// `taxonObservateur`/`nomEspece` pour qu'une observation **corrigée** vers une autre espèce (visible en
    /// « Votre taxon ») soit trouvable en cherchant cette espèce. Fournie au [GestionnaireFiltres] générique,
    /// qui ignore les champs propres au type filtré.
    static BiPredicate<LigneObservationAudio, String> rechercheTexte() {
        return CriteresAudio::correspond;
    }

    private static boolean correspond(LigneObservationAudio ligne, String texte) {
        String aiguille = NormalisationTexte.normaliser(texte);
        return contient(ligne.nomFichier(), aiguille)
                || contient(ligne.taxonTadarida(), aiguille)
                || contient(ligne.nomTadarida(), aiguille)
                || contient(ligne.taxonObservateur(), aiguille)
                || contient(ligne.nomEspece(), aiguille)
                || contient(ligne.commentaire(), aiguille);
    }

    private static boolean contient(String champ, String aiguille) {
        return champ != null && NormalisationTexte.normaliser(champ).contains(aiguille);
    }

    /// Espèces présentes dans `lignes`, une par **taxon retenu**, **distinctes** et triées par libellé
    /// (source stable de la liste déroulante du critère Espèce).
    private static List<EspecePresente> especesPresentes(List<LigneObservationAudio> lignes) {
        return lignes.stream()
                // Une séquence non identifiée n'a aucun taxon retenu : elle ne peuple pas la liste d'espèces.
                .filter(ligne -> codeRetenu(ligne) != null)
                .map(ligne -> new EspecePresente(codeRetenu(ligne), libelleEspece(ligne)))
                .distinct()
                .sorted(Comparator.comparing(EspecePresente::libelle))
                .toList();
    }

    /// Code du **taxon retenu** d'une ligne : celui de l'observateur s'il a tranché, sinon la proposition
    /// Tadarida (`COALESCE(observateur, tadarida)`) ; **`null`** pour une séquence non identifiée (ni
    /// observateur ni Tadarida).
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
