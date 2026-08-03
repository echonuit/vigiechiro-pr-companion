package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.FormatLigneAudio;
import fr.univ_amu.iut.commun.model.NormalisationTexte;
import fr.univ_amu.iut.commun.model.PlageNuit;
import fr.univ_amu.iut.commun.model.VueSauvegardee;
import fr.univ_amu.iut.commun.view.CritereFiltre;
import fr.univ_amu.iut.commun.view.CritereListe;
import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.VuesParDefaut;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

/// Catalogue des **critères de filtrage** de la table audio (patron « à la Notion »). Chaque critère est
/// une entrée du menu « + Filtre » qui s'ajoute comme puce : **Statut**, **Taxon parent**,
/// **Espèce** (taxon), **Lieu** (commune/carré/point/site, #2794), **Références**, **Non identifiés**
/// (sans proposition Tadarida), **Proba** (seuil de probabilité Tadarida) et **Heure** (plage horaire).
final class CriteresAudio {

    /// Groupe des chauves-souris (`taxonomic_group.name`, cf. #507) : sélection par défaut du critère
    /// Taxon parent, car isoler les chiroptères est le levier n°1 de la revue (#471).
    private static final String GROUPE_CHIROPTERES = "Chiroptères";

    /// Bornes de la plage **nuit** par défaut du critère Heure (21 h → 6 h, à cheval sur minuit) : écarte
    /// d'emblée les heures de jour, cas d'usage principal (#531).
    /// Cles stables des criteres a domaine, partagees avec le cablage qui leur fournit les lignes
    /// « tous sauf lui » (#3095). Un litteral duplique les ferait diverger en silence : le domaine
    /// serait alors calcule en excluant le mauvais critere, donc faux sans que rien ne le montre.
    static final String CLE_GROUPE = "groupe";

    static final String CLE_TAXON = "taxon";

    static final String CLE_LIEU = "lieu";

    private static final int HEURE_DEBUT_NUIT = 21;

    private static final int HEURE_FIN_NUIT = 6;

    private CriteresAudio() {}

    /// Vues **par défaut** (lecture seule) de l'écran « Sons & validation », rendues comme onglets avant les
    /// vues de l'utilisateur (#623) :
    /// - **« Tout »** (aucun filtre) : la vue active au chargement, elle correspond à l'état sans filtre, donc
    ///   n'écarte rien (indispensable pour ne pas masquer des séquences déjà validées, ni casser la source
    ///   « non identifiés » qui n'a pas de statut « à revoir » après validation manuelle) ;
    /// - **« À valider »** (statut À revoir, le cœur de la revue) ;
    /// - **« Chiroptères »** (groupe Chiroptères, #471) ;
    /// - **« Sons non identifiés »** (séquences sans proposition Tadarida : présentes sur disque mais absentes
    ///   du CSV, à valider à la main : cf. [#nonIdentifie()]).
    ///
    /// Chaque descripteur est sérialisé exactement comme [GestionnaireFiltres#decrire()] le produirait, pour
    /// que rejouer la vue laisse un état « non modifié ».
    static List<VueSauvegardee> vuesParDefaut() {
        return List.of(
                vueParDefaut("Tout"),
                vueParDefaut(
                        "À valider", new DescripteurCritere("statut", List.of(StatutObservation.NON_TOUCHEE.name()))),
                vueParDefaut(GROUPE_CHIROPTERES, new DescripteurCritere(CLE_GROUPE, List.of(GROUPE_CHIROPTERES))),
                vueParDefaut("Sons non identifiés", new DescripteurCritere("non_identifie", List.of())));
    }

    /// Une vue par défaut de cet écran : délégation à la fabrique partagée [VuesParDefaut] (#1257).
    private static VueSauvegardee vueParDefaut(String nom, DescripteurCritere... criteres) {
        return VuesParDefaut.vue("audio", nom, criteres);
    }

    /// Critère **Statut de revue** : éditeur = liste déroulante (À revoir / Validée / Corrigée) dans la
    /// puce ; par défaut **À revoir** (le plus utile pour la revue), appliqué dès l'ajout.
    static CritereFiltre<LigneObservationAudio> statut() {
        // PRÉSÉLECTIONNÉ sur « À revoir », seule entorse au principe « une puce ajoutée n'écarte rien » :
        // c'est le geste même de l'écran, et s'ouvrir sur tout obligerait à filtrer avant de commencer.
        return CritereListe.enumerationPreselectionnee(
                "statut",
                "Statut",
                List.of(StatutObservation.values()),
                FormatLigneAudio::libelleStatut,
                statut -> ligne -> ligne.statut() == statut,
                StatutObservation.NON_TOUCHEE);
    }

    /// Critère **Taxon parent** : éditeur = liste déroulante des groupes taxonomiques **présents dans les lignes
    /// courantes** (dérivés de `lignesCourantes`, distincts et triés : Chiroptères / Oiseaux / Orthoptères
    /// et cigales…). Sélectionner « Chiroptères » revient à « chauves-souris uniquement » (#471), mais tout
    /// autre groupe est accessible. Par défaut **Chiroptères** s'il est présent (levier n°1 de la revue),
    /// sinon le premier groupe ; l'application est déclenchée dès l'ajout de la puce.
    static CritereFiltre<LigneObservationAudio> groupe(
            Supplier<? extends List<LigneObservationAudio>> lignesCourantes) {
        return groupe(lignesCourantes, valeur -> {});
    }

    /// Variante qui **annonce** le remplacement du groupe retenu quand il disparaît du jeu courant
    /// (#3095) : le défaut reprend la main, donc l'écran filtre sur autre chose que ce qui avait été
    /// demandé, et le taire serait le défaut que le palier 1 vient de corriger.
    static CritereFiltre<LigneObservationAudio> groupe(
            Supplier<? extends List<LigneObservationAudio>> lignesCourantes, Consumer<String> auBasculement) {
        // PRÉSÉLECTIONNÉ, seule entorse au principe « une puce ajoutée n'écarte rien » : isoler les
        // chiroptères est le levier n°1 de la revue (#471). Le défaut se calcule SUR les valeurs offertes
        // (Chiroptères s'il est présent, le premier groupe sinon) : un défaut constant rendrait une puce
        // vide les jours sans chiroptère.
        return CritereListe.valeursPreselectionnees(
                CLE_GROUPE,
                "Taxon parent",
                CritereListe.Domaine.deChaines(() -> groupesPresents(lignesCourantes.get())),
                groupe -> ligne -> groupe.equals(ligne.groupe()),
                CriteresAudio::defaut,
                auBasculement);
    }

    /// Groupes taxon parents présents dans `lignes` : non nuls, **distincts** et **triés** (source stable
    /// pour la liste déroulante du critère Taxon parent).
    private static List<String> groupesPresents(List<LigneObservationAudio> lignes) {
        return lignes.stream()
                .map(LigneObservationAudio::groupe)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /// Groupe présélectionné du critère Taxon parent : **Chiroptères** s'il figure parmi les groupes présents
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
        // Le domaine est un RECORD, et c'est le cas qui a fait généraliser la fabrique (#3060) : ce qu'on
        // voit (le nom vernaculaire) et ce qu'on mémorise (le code Tadarida) sont deux champs distincts.
        return CritereListe.valeurs(
                CLE_TAXON,
                "Espèce",
                "Choisir une espèce",
                new CritereListe.Domaine<>(
                        () -> especesPresentes(lignesCourantes.get()), EspecePresente::libelle, EspecePresente::code),
                espece -> ligne -> espece.code().equals(codeRetenu(ligne)));
    }

    /// Critère **Lieu** (#2794, chantier #2790) : liste à cocher des lieux **présents dans les lignes
    /// courantes**, toutes dimensions confondues - communes (lot 0 #2791), carrés, points, sites, dans
    /// cet ordre. Une ligne passe si **l'un** de ses quatre champs figure parmi les valeurs cochées
    /// ([CritereListe#multipleParmi]) ; rien de coché n'écarte rien. C'est ce qui rend le scénario
    /// « espèce × lieu » jouable en direct : Analyse → clic espèce donne « l'espèce partout », la puce
    /// Lieu restreint à « Aix-en-Provence » sans repasser par la carte.
    static CritereFiltre<LigneObservationAudio> lieu(Supplier<? extends List<LigneObservationAudio>> lignesCourantes) {
        return CritereListe.multipleParmi(
                CLE_LIEU,
                "Lieu",
                "Choisir un lieu",
                () -> lieuxPresents(lignesCourantes.get()),
                CriteresAudio::dimensionsLieu);
    }

    /// Lieux présents dans `lignes` : les valeurs **distinctes** de chaque dimension, groupées par
    /// dimension (communes, puis carrés, puis points, puis sites) et triées au sein de chacune - la
    /// liste reste lisible sans en-têtes. Un même libellé porté par deux dimensions (un site homonyme
    /// d'une commune) n'apparaît qu'une fois : le coche vaut alors pour les deux.
    private static List<CritereListe.GroupeValeurs> lieuxPresents(List<LigneObservationAudio> lignes) {
        return List.of(
                new CritereListe.GroupeValeurs("Communes", valeursDistinctes(lignes, LigneObservationAudio::commune)),
                new CritereListe.GroupeValeurs("Carrés", valeursDistinctes(lignes, LigneObservationAudio::numeroCarre)),
                new CritereListe.GroupeValeurs("Points", valeursDistinctes(lignes, CriteresAudio::pointQualifie)),
                new CritereListe.GroupeValeurs("Sites", valeursDistinctes(lignes, LigneObservationAudio::nomSite)));
    }

    /// Le point **qualifié par son carré**, « 640380 · A1 » (#2992). Le schéma pose `UNIQUE(site_id, code)` :
    /// un code de point est unique **par site**, pas globalement. Cet écran ne cible pas toujours un seul
    /// passage (sources « un lot de passages » et « une espèce à travers les passages ») ; une entrée
    /// « A1 » y confondait donc silencieusement les A1 de plusieurs carrés.
    private static String pointQualifie(LigneObservationAudio ligne) {
        return ligne.codePoint() == null ? null : ligne.numeroCarre() + " · " + ligne.codePoint();
    }

    /// Les valeurs non nulles et distinctes d'une dimension, triées (ordre stable de la liste à cocher).
    private static List<String> valeursDistinctes(
            List<LigneObservationAudio> lignes, Function<LigneObservationAudio, String> dimension) {
        return lignes.stream()
                .map(dimension)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    /// Les valeurs candidates d'une ligne face à la liste des lieux cochés : ses quatre champs
    /// géographiques non nuls (commune, carré, point, site).
    private static List<String> dimensionsLieu(LigneObservationAudio ligne) {
        return Stream.of(ligne.commune(), ligne.numeroCarre(), pointQualifie(ligne), ligne.nomSite())
                .filter(Objects::nonNull)
                .toList();
    }

    /// Critère **Références seulement** (booléen) : ne garde que les observations archivées en référence
    /// (`is_reference`). Critère **sans éditeur** : la simple présence de la puce active le filtre (#473),
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

    /// Critère **Douteux seulement** (booléen, #160) : ne garde que les observations marquées « douteuses /
    /// à repasser » (`is_doubtful`). Critère **sans éditeur** : la présence de la puce active le filtre, son
    /// retrait le désactive. Pendant du critère [#references()] ; l'indicateur visuel reste le bouton de la
    /// barre d'actions.
    static CritereFiltre<LigneObservationAudio> douteux() {
        return new CritereFiltre<LigneObservationAudio>() {
            @Override
            public String nom() {
                return "douteux";
            }

            @Override
            public String libelle() {
                return "Douteux";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                applique.accept(LigneObservationAudio::douteux); // filtre actif dès l'ajout de la puce
                return null; // booléen : pas d'éditeur, la présence de la puce suffit
            }
        };
    }

    /// Critère **Non identifiés** : garde les séquences **sans proposition Tadarida** (`taxonTadarida == null`)
    /// : présentes sur disque mais absentes du CSV Tadarida, à valider à la main. C'est le **complément exact**
    /// des observations Tadarida (dont la proposition n'est jamais nulle), y compris une fois validées à la main
    /// (elles n'acquièrent pas de proposition Tadarida). Critère **sans éditeur** (booléen) : la présence de la
    /// puce active le filtre. Porte la vue par défaut « Sons non identifiés » ([#vuesParDefaut()]).
    /// Critère **Espèces à enjeu** (#2353) : garde les observations dont le **taxon retenu** figure parmi
    /// les espèces prioritaires du Plan National d'Actions Chiroptères. Critère **sans éditeur** (booléen) :
    /// la présence de la puce active le filtre, comme « Douteux » ou « Non identifiés ».
    ///
    /// Sur une nuit à 4 000 contacts dont douze relèvent d'espèces à enjeu, c'est ce qui remplace une
    /// recherche ligne par ligne.
    static CritereFiltre<LigneObservationAudio> aEnjeu(Predicate<LigneObservationAudio> estPrioritaire) {
        return new CritereFiltre<LigneObservationAudio>() {
            @Override
            public String nom() {
                return "a_enjeu";
            }

            @Override
            public String libelle() {
                return "Espèces à enjeu";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                applique.accept(estPrioritaire); // filtre actif dès l'ajout de la puce
                return null; // booléen : pas d'éditeur, la présence de la puce suffit
            }
        };
    }

    static CritereFiltre<LigneObservationAudio> nonIdentifie() {
        return new CritereFiltre<LigneObservationAudio>() {
            @Override
            public String nom() {
                return "non_identifie";
            }

            @Override
            public String libelle() {
                return "Non identifiés";
            }

            @Override
            public Node editeur(Consumer<Predicate<LigneObservationAudio>> applique) {
                applique.accept(ligne -> ligne.taxonTadarida() == null); // filtre actif dès l'ajout de la puce
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
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                if (!valeurs.isEmpty()) {
                    ((Slider) ((HBox) editeur).getChildren().get(0)).setValue(Double.parseDouble(valeurs.get(0)));
                }
                return List.of(); // un seuil est une valeur continue : elle se repose toujours
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
                HBox editeur = new HBox(6.0, new Label("de"), de, new Label("à"), a);
                // Sans alignement, les libellés « de »/« à » (courts) se collent en haut de la rangée alors
                // que les listes déroulantes sont plus hautes : on les recentre verticalement.
                editeur.setAlignment(Pos.CENTER_LEFT);
                return editeur;
            }

            @Override
            public List<String> valeurCourante(Node editeur) {
                HBox conteneur = (HBox) editeur;
                int debut = (Integer) ((ComboBox<?>) conteneur.getChildren().get(1)).getValue();
                int fin = (Integer) ((ComboBox<?>) conteneur.getChildren().get(3)).getValue();
                return List.of(Integer.toString(debut), Integer.toString(fin));
            }

            @Override
            public List<String> restaurerValeurs(Node editeur, List<String> valeurs) {
                if (valeurs.size() >= 2) {
                    HBox conteneur = (HBox) editeur;
                    selectionnerParValeur(conteneur.getChildren().get(1), Integer.valueOf(valeurs.get(0)));
                    selectionnerParValeur(conteneur.getChildren().get(3), Integer.valueOf(valeurs.get(1)));
                }
                return List.of(); // les 24 heures sont toujours offertes, quel que soit le jeu courant
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
    /// vernaculaire observateur `nomEspece`, ou Tadarida à défaut), commentaire, et les champs
    /// **géographiques** - carré, point, site, commune (#2794, alignement sur la recherche d'Analyse qui
    /// les couvrait déjà : « Aix » tapé ici trouve enfin les observations d'Aix). On inclut
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
                || contient(ligne.commentaire(), aiguille)
                || contient(ligne.numeroCarre(), aiguille)
                || contient(ligne.codePoint(), aiguille)
                || contient(ligne.nomSite(), aiguille)
                || contient(ligne.commune(), aiguille);
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
