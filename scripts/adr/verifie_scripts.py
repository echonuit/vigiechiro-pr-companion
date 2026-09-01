#!/usr/bin/env python3
"""Auto-test des scripts de vérification ADR (issue #2467).

Ce que le portail qualité gardait déjà : qu'un script ne PLANTE pas et que son compte ne DÉPASSE pas le
cliquet (sur-comptage). Ce qu'il ne gardait pas : qu'un script **détecte** vraiment. Un script qui, par
régression, cesserait de voir un motif ferait BAISSER son compte sous le cliquet - le portail resterait
vert pendant qu'une vraie violation passe. Un faux vert, exactement ce que le mécanisme combat.

Chaque cas plante une violation CONNUE dans une fixture et exige que le script la voie (test positif) ;
et plante la MÊME chose en COMMENTAIRE, exigeant qu'il ne la compte pas (le défaut exact de la passe 1
de clôture, où trois scripts comptaient les commentaires). Le premier ferme le trou de déflation, le
second interdit le retour de la cécité aux commentaires.

Aucune dépendance hors stdlib : lancé comme les scripts, `python3 scripts/adr/verifie_scripts.py`.
"""

import importlib.util
import pathlib
import re
import sys
import tempfile

ICI = pathlib.Path(__file__).parent
ICI_NOM = pathlib.Path(__file__).name

# Nomme parce qu il porte PLUSIEURS cas la ou ses voisins n en ont qu un : le cliquet Java, la
# tolerance zero d une zone, le refus d une zone qui ne balaie rien, le discernement de la couverture,
# le balayage non recursif. Sans denombrement ici : la phrase a deja derive deux fois en une journee,
# et un commentaire qui compte ses voisins vieillit a chaque ajout.
ADR_2843 = "2843-tiret-cadratin.py"

_echecs: list[str] = []


# Les scripts que ce harnais a REELLEMENT charges pendant la passe. C'est la source du controle de
# completude : une liste declaree serait un second inventaire a tenir, c'est-a-dire le defaut qu'on
# corrige. Elle se remplit au fil des cas, donc un `test_*` defini mais jamais joue laisse son
# detecteur decouvert - et le dit.
_charges: set[str] = set()


def _charge(nom: str):
    """Importe un script au nom non-importable (chiffres, tirets) par son chemin."""
    _charges.add(nom)
    module = "adr_" + nom.replace("-", "_").replace(".py", "")
    spec = importlib.util.spec_from_file_location(module, ICI / nom)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def _verifie(cas: str, obtenu, attendu) -> None:
    if obtenu == attendu:
        print(f"  ✔ {cas}")
    else:
        _echecs.append(cas)
        print(f"  ✘ {cas} : attendu {attendu}, obtenu {obtenu}")


def _ecrire(racine: pathlib.Path, chemin_relatif: str, contenu: str) -> None:
    f = racine / chemin_relatif
    f.parent.mkdir(parents=True, exist_ok=True)
    f.write_text(contenu, encoding="utf-8")


def test_0008_echec_silencieux() -> None:
    m = _charge("0008-echec-silencieux.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "src/main/java/Exemple.java",
            "class E {\n"
            "  void a() { try { x(); } catch (Exception e) { } }\n"  # vide -> compte
            "  void b() { try { x(); } catch (Exception e) { log(e); } }\n"  # tracé -> non
            "  void c() { try { x(); } catch (Exception e) { /* ignoré */ } }\n"  # commentaire seul -> compte
            "  /* void mort() { try{}catch(E e){} } */\n"  # catch EN commentaire -> non
            "}\n",
        )
        # L arbre de TEST est dans la meme population, et ce cas est ce qui le tient. Le compte de 3
        # separe les deux arbres : retirer `TESTS` de `RACINES` le ramenerait a 2, et le temoin
        # rougirait. Un temoin pose sur la seule production aurait laisse passer ce retrait en
        # silence, ce qui est exactement le faux vert que cette suite existe pour interdire.
        _ecrire(
            racine,
            "src/test/java/ExempleTest.java",
            "class ET { void d() { try { x(); } catch (Exception e) { } } }\n",
        )
        n = len(m.suspects(racine=racine))
        _verifie("0008 détecte les catch vides dans les DEUX arbres, production et test", n, 3)


def test_3053_capture_libelle() -> None:
    """Le cas discriminant est le troisième : la MÊME écriture, hors d'un outil de capture, ne compte pas.

    Sans lui, le motif attraperait tout `ifPresent` du dépôt, deviendrait du bruit, et finirait
    désactivé - ce qui coûte plus cher que l'absence de cliquet.
    """
    m = _charge("3053-capture-libelle.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        abstention = (
            "  void geste() {\n"
            '    menu.getItems().stream().filter(i -> "Lieu".equals(i.getText()))\n'
            "        .findFirst().ifPresent(MenuItem::fire);\n"
            "  }\n"
        )
        _ecrire(racine, "audio/outils/CaptureX.java", "class CaptureX {\n" + abstention + "}\n")
        # Le remède : exige et lève. Ne compte pas.
        _ecrire(
            racine,
            "audio/outils/CaptureY.java",
            "class CaptureY {\n"
            "  void geste() {\n"
            '    ApercuFx.exigerParLibelle("le menu", menu.getItems(), MenuItem::getText, "Lieu").fire();\n'
            "  }\n"
            "}\n",
        )
        # Le motif CITÉ dans un commentaire (c'est le cas de l'en-tête d'ApercuFx) : ne compte pas.
        _ecrire(
            racine,
            "audio/outils/CaptureZ.java",
            "class CaptureZ {\n  /* proscrit : .findFirst().ifPresent(x) */\n}\n",
        )
        # Ni le nom ni le paquet d'un outil de capture : hors périmètre, l'usage y est normal.
        _ecrire(
            racine, "audio/view/UnControleur.java", "class UnControleur {\n" + abstention + "}\n"
        )
        _ecrire(
            racine,
            "audio/model/CaptureAilleurs.java",
            "class CaptureAilleurs {\n" + abstention + "}\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("3053 détecte le geste qui ne se fait pas, et lui seul", n, 1)


def test_0010_dialogue_hors_port() -> None:
    m = _charge("0010-dialogue-hors-port.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Vue.java",
            "class Vue {\n"
            "  void go() { new Alert(Alert.AlertType.ERROR).showAndWait(); }\n"  # code -> compte
            "  /// jadis un `Alert.showAndWait()` figeait le test\n"  # commentaire -> non
            "}\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0010 détecte un Alert hors port, ignore le commentaire", n, 1)


def test_0035_pictogramme() -> None:
    m = _charge("0035-pictogramme-caractere.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Vue.fxml",
            "<VBox>\n"
            '  <Button text="\U0001f5d1 Supprimer"/>\n'  # pictogramme dans un libellé -> compte
            "  <!-- \U0001f5d1 en commentaire, prose autorisée -->\n"  # commentaire -> non
            "</VBox>\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0035 détecte un pictogramme, ignore le commentaire", n, 1)


def test_2843_tiret_cadratin() -> None:
    m = _charge(ADR_2843)
    cad = chr(0x2014)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Exemple.java",
            "class Exemple {\n"
            "    // un tiret cadratin "
            + cad
            + " en commentaire, et la regle vise les commentaires\n"
            '    String propre = "deux-points : voila la forme attendue";\n'  # rien -> non
            '    static final String ABSENTE = "' + cad + '";\n'  # litteral = le glyphe -> non
            "    /// La cellule vide affiche `" + cad + "` dans le tableau.\n"  # chevrons -> non
            "    /// Le verdict vaut « "
            + cad
            + " a verifier » tant que rien n est pose.\n"  # cite -> non
            '    Pattern LEGACY = Pattern.compile("probable [-'
            + cad
            + '] `");\n'  # classe litterale -> non
            "}\n",
        )
        n = len(m.suspects(racine))
        # Contrairement a ses voisins, ce script COMPTE les commentaires : la regle porte sur « la doc
        # et les commentaires », qui sont ici la matiere et non le bruit. Les quatre formes CITEES, en
        # revanche, ne sont pas de la prose (meme regle que les zones Markdown) : le glyphe defini en
        # litteral, entre chevrons de code, dans un libelle recopie, ou dans la classe de caracteres
        # par laquelle un analyseur accepte l ancienne forme. Ce cas garde les deux sens a la fois - si
        # le motif de citation devenait gourmand il avalerait la prose et n tomberait a 0 ; s il cessait
        # de proteger, n monterait a 5 et le cliquet buterait sur un plancher fantome.
        _verifie("2843 compte la prose Java, epargne les quatre formes citees", n, 1)


def test_2843_prose_documentation() -> None:
    m = _charge(ADR_2843)
    cad = chr(0x2014)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "page.md",
            "Une phrase " + cad + " avec un cadratin de prose.\n"  # prose -> compte
            "La cellule vide affiche `" + cad + "` dans le tableau.\n"  # chevrons de code -> non
            "Le lien « GPS manquant "
            + cad
            + " placer » vient de l application.\n"  # citation -> non
            "Un motif `[-"
            + cad
            + "]` accepte l ancienne forme d en-tete.\n"  # classe litterale -> non
            "Voir [le guide "
            + cad
            + " chapitre 3](guide.md) pour la suite.\n"  # LIEN Markdown -> compte
            "Une phrase saine : deux-points.\n",  # rien -> non
        )
        n = len(m.prose(racine))
        # La regle « ce qui est cite n est pas de la prose » couvre le glyphe ET les libelles de
        # l application. Si le motif de citation devenait trop gourmand, il avalerait la prose et ce
        # cas tomberait a zero : c est exactement la deflation que ce fichier existe pour interdire.
        #
        # Le LIEN Markdown est ici pour une raison precise. La forme citee « classe de caracteres » est
        # ecrite en LITTERAL, et non « des crochets contenant un cadratin », faute de quoi elle
        # avalerait tout libelle de lien portant un tiret de prose. Ce fichier de fixture contient donc
        # les deux : la classe, epargnee, et le lien, compte. Elargir le motif fait tomber n a 1.
        _verifie("2843 zone nettoyee : compte la prose et les liens, epargne les citations", n, 2)


def test_2843_zone_vide_est_une_erreur() -> None:
    """Une zone qui ne balaie AUCUN fichier doit lever, pas rapporter zero.

    C est le faux vert le plus difficile a voir de tout ce fichier : un motif mal apparie a son arbre
    (« *.md » sur un arbre Java) fait dire au garde « 0 cadratin de prose », ce qui a la forme exacte
    du succes. Rien, ailleurs, ne distingue une zone propre d une zone jamais regardee.
    """
    m = _charge(ADR_2843)
    cad = chr(0x2014)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "Source.java", "// une prose " + cad + " avec un cadratin\n")
        # Le bon motif voit le fichier et compte sa prose.
        _verifie(
            "2843 zone nettoyee : le motif « *.java » voit l arbre Java",
            len(m.prose(racine, (), "*.java")),
            1,
        )
        # Le mauvais motif ne voit rien : ce doit etre une erreur, jamais un zero rassurant.
        try:
            m.prose(racine, (), "*.md")
        except AssertionError:
            _verifie("2843 zone nettoyee : un motif qui ne balaie rien leve", 1, 1)
        else:
            _verifie("2843 zone nettoyee : un motif qui ne balaie rien leve", 0, 1)


def test_2843_couverture_distingue_dedans_dehors() -> None:
    """`couvert()` doit DISTINGUER, pas repondre oui a tout.

    Le regime de couverture repond « quel fichier personne ne regarde ? ». Il ne peut pas garder son
    propre discernement : rendre `couvert()` toujours vrai l AVEUGLE, et un detecteur aveugle rapporte
    « 0 fichier sans garde », soit exactement le vert de la bonne sante. Muter le script ne fait donc
    pas rougir le script. Ce cas-ci est le seul endroit ou cette mutation se voit.
    """
    m = _charge(ADR_2843)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "zone/dedans.md", "peu importe\n")
        _ecrire(racine, "ailleurs/dehors.md", "peu importe\n")
        zones, sources = m.ZONES_NETTOYEES, m.SOURCES
        try:
            m.ZONES_NETTOYEES = (("zone temoin", racine / "zone", (), "*.md"),)
            m.SOURCES = []
            _verifie(
                "2843 couverture : un fichier DANS la zone est couvert",
                m.couvert(racine / "zone" / "dedans.md"),
                True,
            )
            _verifie(
                "2843 couverture : un fichier HORS zone ne l est pas",
                m.couvert(racine / "ailleurs" / "dehors.md"),
                False,
            )
        finally:
            m.ZONES_NETTOYEES, m.SOURCES = zones, sources


def test_2843_balayage_non_recursif() -> None:
    """`recursif=False` doit vraiment s arreter au niveau de la racine.

    Sans ce cas, le drapeau serait un no-op SILENCIEUX : la zone racine se mettrait a descendre dans
    tout le depot, ramasserait les fichiers non suivis, et ferait rougir le garde chez le developpeur
    sans rien signaler en CI. Un drapeau qu on croit actif et qui ne fait rien est pire qu absent.
    """
    m = _charge(ADR_2843)
    cad = chr(0x2014)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "RACINE.md", "Une prose " + cad + " a la racine.\n")
        _ecrire(racine, "sousdossier/PROFOND.md", "Une prose " + cad + " en profondeur.\n")
        _verifie(
            "2843 zone nettoyee : recursif voit les deux niveaux",
            len(m.prose(racine, (), "*.md")),
            2,
        )
        _verifie(
            "2843 zone nettoyee : non recursif s arrete a la racine",
            len(m.prose(racine, (), "*.md", False)),
            1,
        )


def test_0037_slot_actions() -> None:
    m = _charge("0037-slot-actions-hbox.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Vue.fxml",
            "<VBox>\n"
            '  <HBox styleClass="barre-actions"/>\n'  # slot d'actions en HBox -> compte
            '  <!-- <HBox styleClass="barre-actions"/> en commentaire -->\n'  # commentaire -> non
            "</VBox>\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0037 détecte un slot d'actions HBox, ignore le commentaire", n, 1)


def test_2493_modale_suit_croissance() -> None:
    m = _charge("2493-modale-suit-croissance.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Une modale qui révèle un bandeau sans câbler suivreLaCroissance -> suspect.
        _ecrire(
            racine,
            "SuspectModaleController.java",
            "class SuspectModaleController { void init() { BandeauRetour.installer(a, b); } }\n",
        )
        # Une modale qui révèle ET câble -> pas suspect.
        _ecrire(
            racine,
            "SaineModaleController.java",
            "class SaineModaleController { void init() {\n"
            "  BandeauRetour.installer(a, b);\n"
            "  Modales.suivreLaCroissance(racine, b.managedProperty());\n"
            "} }\n",
        )
        # Une vue qui n'est pas une modale (nom sans « Modale ») -> hors champ, même si elle révèle.
        _ecrire(
            racine,
            "AnalyseController.java",
            "class AnalyseController { void init() { BandeauRetour.installer(a, b); } }\n",
        )
        n = len(m.suspects(vues=racine))
        _verifie("2493 détecte la modale non câblée, épargne la câblée et la non-modale", n, 1)


def test_loupe_0020() -> None:
    m = _charge("loupe-0020-ecritures-plateforme.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Client.java",
            "class Client {\n"
            "  public Reponse creerParticipation(String s) { return null; }\n"  # écriture -> compte
            "  public Reponse lireParticipation(String s) { return null; }\n"  # lecture -> non
            "}\n",
        )
        n = len(m.candidats(api=racine))
        _verifie("loupe 0020 liste les écritures, pas les lectures", n, 1)


def test_loupe_0044() -> None:
    m = _charge("loupe-0044-mecanisme-parallelisme.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Service.java",
            "class Service {\n"
            "  void go() { Thread.ofVirtual().start(r); }\n"  # mécanisme réel -> compte
            "  /// autrefois un Thread.ofVirtual() maison, remplacé par ExecuteurTache\n"  # cité en Javadoc -> non
            "}\n",
        )
        n = len(m.candidats(racine=racine))
        _verifie("loupe 0044 voit un mécanisme réel, ignore le Javadoc qui le cite", n, 1)


def test_2635_refus_sans_surface() -> None:
    m = _charge("2635-refus-sans-surface.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "fr/univ_amu/iut/site/model/Refus.java",
            "class Refus {\n"
            '  String a() { return "Connectez-vous depuis le menu \u2630"; }\n'
            '  String b() { return "Connectez-vous depuis le menu principal"; }\n'
            '  // autrefois : return "ouvrez le menu \u2630";\n'
            "}\n",
        )
        # Hors `**/model/**`, le glyphe est legitime : la surface a le droit de se nommer. Sans ce
        # second fichier, un script qui aurait perdu son filtre de zone resterait vert.
        _ecrire(
            racine,
            "fr/univ_amu/iut/site/view/Ecran.java",
            'class Ecran {\n  String a() { return "menu \u2630"; }\n}\n',
        )
        n = len(m.suspects(sources=racine))
        _verifie("2635 voit le glyphe dans un modèle, ignore le commentaire et la vue", n, 1)


def test_3947_message_enveloppe() -> None:
    m = _charge("3947-message-enveloppe.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Les TROIS formes que l'ADR nomme, une par ligne. Un compte exact rattrape la regression
        # d'une SEULE d'entre elles - ce qu'un temoin unique laisserait passer.
        _ecrire(
            racine,
            "fr/univ_amu/iut/commun/Enveloppe.java",
            "class Enveloppe {\n"
            "  String a(Exception e) { return e.getMessage() != null ? e.getMessage() : e.toString(); }\n"
            "  String b(Exception e) { return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(); }\n"
            '  String c(Throwable t) { return t.getCause() != null ? t.getCause().getMessage() : "?"; }\n'
            "  String d(Exception e) { return CauseLisible.de(e); }\n"
            "  // ancien : e.getMessage() != null ? e.getMessage() : e.toString();\n"
            "}\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("3947 voit les trois formes, ignore le remède et le commentaire", n, 3)


def test_4359_javadoc_narratif() -> None:
    m = _charge("4359-javadoc-narratif.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Quatre fixtures pour quatre comportements, et un compte EXACT qui les separe. Un temoin
        # unique dirait « il detecte » sans dire ce qu il epargne, or c est l epargne qui se casse :
        # compter les etiquettes de contrat comme de la prose penaliserait un record bien documente,
        # ce que l ADR refuse explicitement.
        #
        # Le QUATRIEME cas est celui du seuil PAR NATURE, et il est le plus fragile : le meme bloc,
        # au meme nombre de lignes, doit compter au-dessus d une methode et ne rien couter au-dessus
        # d une classe. Un seuil redevenu unique le ferait rougir des deux cotes.
        sur_type = m.SEUILS["type"]
        sur_methode = m.SEUILS["methode"]
        long_ = "\n".join(f"/// Ligne {i}." for i in range(sur_type + 3))
        court = "\n".join(f"/// Ligne {i}." for i in range(sur_type))
        contrat = "\n".join(f"/// @param p{i} le parametre {i}" for i in range(sur_type + 5))
        entre_deux = "\n".join(f"/// Ligne {i}." for i in range(sur_methode + 2))
        _ecrire(racine, "fr/univ_amu/iut/a/Bavard.java", long_ + "\nclass Bavard {}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/Sobre.java", court + "\nclass Sobre {}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/Contrat.java", contrat + "\nclass Contrat {}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/SousClasse.java", entre_deux + "\nclass SousClasse {}\n")
        n = len(m.suspects(racine=racine))
        _verifie("4359 compte au-dela du seuil, epargne le bloc court et les etiquettes", n, 3)

        # Le MEME bloc, pose au-dessus d une methode : il coute, la ou il ne coutait rien au-dessus
        # d une classe. C est tout ce que le seuil par nature apporte, et rien d autre ne le tient.
        _ecrire(
            racine,
            "fr/univ_amu/iut/a/SousClasse.java",
            "class SousClasse {\n" + entre_deux + "\n    void faire() {}\n}\n",
        )
        n = len(m.suspects(racine=racine))
        _verifie(
            "4359 compte au-dessus d une methode ce qu il epargne au-dessus d une classe", n, 5
        )


def test_4366_avertissement_en_pictogramme() -> None:
    m = _charge("4366-avertissement-en-pictogramme.py")
    A = chr(0x26A0) + chr(0xFE0F)
    # Le detecteur est trivial, son EPARGNE ne l est pas : c est la qu il se trompe s il se trompe.
    # Un cas par cecite declaree en tete du script, plus le cas positif qui les rend non vides.
    cas = [
        ("prose : le signe alerte", f"{A} ne pas redemarrer entre deux passages.", ".md", False, 1),
        ("cite entre accents graves", f"les libelles commencaient par un `{A}`.", ".md", False, 0),
        ("cite entre guillemets francais", f"le signe « {A} » ouvrait la ligne.", ".md", False, 0),
        (
            "voisin d un autre marqueur",
            f"un \u2717 interdit ; un {A} laisse deposer.",
            ".md",
            False,
            0,
        ),
        ("dans un bloc de code markdown", f"{A} sortie du programme", ".md", True, 0),
        (
            "chaine litterale d un fichier de code",
            f'echo "{A} rien n a ete filme"',
            ".sh",
            False,
            0,
        ),
        ("noeud montre d une maquette", f'<text x="10">{A} attention</text>', ".svg", False, 0),
    ]
    for titre, ligne, suffixe, bloc, attendu in cas:
        _verifie(f"4366 {titre}", len(m.alertes(ligne, suffixe, bloc)), attendu)


def test_4366_pictogramme_en_tete_de_ligne() -> None:
    """LE cas que la regle des « delimiteurs de chaque cote » laissait passer.

    Un avertissement s ecrit `⚠️ **texte**`, et c est la forme la plus courante du depot. Le cote
    gauche est vide - ce que l ancienne regle acceptait comme un delimiteur - et le `*` de l emphase
    fournissait le cote droit. 264 avertissements reels echappaient ainsi au compte. Une mention est
    ENCADREE : le meme delimiteur ouvre et ferme.
    """
    m = _charge("4366-avertissement-en-pictogramme.py")
    alerte = m.ALERTE if isinstance(m.ALERTE, str) else "\u26a0\ufe0f"
    import re as _re

    def compte(ligne, suffixe=".md"):
        pos = _re.search(m.ALERTE, ligne)
        return 0 if pos is None else len(m.alertes(ligne, suffixe, False))

    _verifie("4366 en tete de ligne, suivi de gras : compte", compte(f"{alerte} **Attention.**"), 1)
    _verifie(
        "4366 en javadoc, suivi de gras : compte",
        compte(f"/// {alerte} **Attention.**", ".java"),
        1,
    )
    _verifie(
        "4366 encadre de guillemets francais : epargne",
        compte(f"Le signe « {alerte} » se cite."),
        0,
    )
    _verifie(
        "4366 encadre de parentheses : epargne", compte(f"Le pictogramme ({alerte}) est cite."), 0
    )


def test_4783_traces_d_outil() -> None:
    m = _charge("4783-traces-d-outil.py")
    ZWSP, ZWJ, BOM = chr(0x200B), chr(0x200D), chr(0xFEFF)
    E_HOMME, E_LOUPE = chr(0x1F468), chr(0x1F52C)
    E_CYR = chr(0x0435)  # « e » cyrillique, sosie du latin
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # SIX refus, un par famille comptable. Les cinq epargnes sont l autre moitie du temoin :
        # un garde qui refuse tout passerait le premier compte et serait inutilisable.
        # Les chaines sont ASSEMBLEES et non ecrites. Ce fichier est lu par le garde qu il
        # eprouve, et une chaine litterale y ferait rougir le depot entier : c est l ADR 3645 au
        # grain de la ligne. `test_4368_apostrophe_en_libelle` emploie le meme detour avec
        # `chr(0x2019)`. Exempter la suite en bloc serait plus simple et laisserait un angle mort
        # de 1 100 lignes.
        jeton = "cite" + "turn0search0"
        renvoi = "[" + "cite: 12]"
        suivi = "utm_" + "source=chatgpt.com"
        gabarit = "[Votre " + "nom]"
        refuses = {
            "t1a.md": f"Voir la source {jeton} pour le detail.",
            "t1b.md": f"Le rapport le dit {renvoi}.",
            "t2.md": f"https://exemple.org/page?{suivi}",
            "t3.md": f"un espace{ZWSP}sans chasse",
            "t4.md": f"la m{E_CYR}sure est fausse",
            "t5.md": f"Signe : {gabarit}",
        }
        epargnes = {
            # Le liant COMPOSE un pictogramme, il ne traine pas.
            "ok-emoji.md": f"Le persona {E_HOMME}{ZWJ}{E_LOUPE} parle.",
            # La marque d ordre est DECRITE, pas posee : l effacer casserait un analyseur de CSV.
            "ok-bom.java": f"    private static final char BOM = '{BOM}';",
            # La grille cite les chaines qu elle cherche ; entre accents graves elles sont nommees.
            "ok-citee.md": f"Chercher `{jeton}` dans le texte.",
            # Un lien Markdown ouvre un crochet sans etre un gabarit.
            "ok-lien.md": "Voir [le guide](https://exemple.org) et [autre](x).",
            # Un mot entierement cyrillique est un mot etranger, pas un sosie.
            "ok-cyrillique.md": "Le mot "
            + chr(0x0440)
            + chr(0x0435)
            + chr(0x043A)
            + chr(0x0430)
            + " signifie riviere.",
        }
        for nom, contenu in {**refuses, **epargnes}.items():
            _ecrire(racine, nom, contenu + "\n")
        trouves = m.suspects(racine=racine)
        _verifie(
            "4783 refuse une occurrence de chacune des cinq familles",
            sorted({t.split(":")[0] for t in trouves}),
            sorted(refuses),
        )
        _verifie(
            "4783 epargne l emoji, la marque decrite, la chaine citee, le lien et le mot etranger",
            [t for t in trouves if t.split(":")[0] in epargnes],
            [],
        )


def test_4368_apostrophe_en_libelle() -> None:
    m = _charge("4368-apostrophe-droite.py")
    C = chr(0x2019)
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # La regle est « rien que l ASCII, partout » : le commentaire compte autant que la chaine.
        # Ce que le garde doit EPARGNER, ce sont les lignes qui NOMMENT le caractere, et c est la
        # qu il se tromperait. Un compte de 2 separe les deux emplois des trois mentions ; un compte
        # de 5 voudrait dire qu il a cesse de faire la difference.
        _ecrire(
            racine,
            "Ecran.java",
            "class Ecran {\n"
            f'  String a() {{ return "l{C}audio est parti"; }} // l{C}appel vient d ailleurs\n'
            f"  // on ecrit `{C}` ou l ASCII, jamais les deux\n"
            f'  static final String COURBE = "{C}";\n'
            f'  static final String CLASSE = "[\'{C}]";\n'
            "}\n",
        )
        _verifie("4368 compte les emplois, epargne les mentions", len(m.suspects(racine=racine)), 2)


def test_4395_renvois_en_javadoc() -> None:
    m = _charge("4395-renvois-en-javadoc.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Le detecteur compte ; ce qui se casse, c est son EPARGNE. Une couleur CSS a six chiffres
        # etait comptee comme un renvoi `#4` avant que la borne haute ne soit posee, et le corpus en
        # portait une. Un compte de 3 separe les trois issues citees des cinq pieges - couleur, lien
        # javadoc vers un membre, commentaire d implementation, zone de test, et le DOUBLON, qui
        # pointe la meme discussion et ne compte donc pas deux fois (#4398). Un compte de 7 voudrait
        # dire que le garde a cesse de faire la difference.
        _ecrire(
            racine,
            "src/main/java/fr/univ_amu/iut/a/Contrat.java",
            "/// Le decoupage suit #504, et le cas limite vient de #33.\n"
            "/// La teinte de fond vaut #123456 dans la palette.\n"
            "/// Voir {@link Decoupage#applique} pour le detail.\n"
            "/// Le verrou de navigation vient de #54.\n"
            "/// Le decoupage, encore lui (#504) : le doublon compte pour un.\n"
            "// vient de #4040, mais en commentaire d implementation\n"
            "class Contrat {}\n",
        )
        _ecrire(
            racine,
            "src/test/java/fr/univ_amu/iut/a/ContratTest.java",
            "/// Eprouve #4040, hors champ declare.\nclass ContratTest {}\n",
        )
        _verifie(
            "4395 compte les issues citees, epargne la couleur, le lien, le commentaire, la zone "
            "de test et le doublon",
            m.renvois(racine),
            3,
        )


def test_4359_blocs_relus() -> None:
    m = _charge("4359-blocs-relus.py")
    corpus = {"aaaa": "src/main/java/A.java", "bbbb": "src/main/java/B.java"}

    _verifie(
        "4359 registre : une entree qui correspond n est pas perimee",
        len(m.perimees([("aaaa", "src/main/java/A.java", "motif")], corpus)),
        0,
    )
    _verifie(
        "4359 registre : une entree qui ne correspond a rien est perimee",
        len(m.perimees([("zzzz", "src/main/java/Fantome.java", "motif")], corpus)),
        1,
    )
    _verifie("4359 registre : un registre vide ne perime rien", len(m.perimees([], corpus)), 0)
    # L EMPREINTE fait l identite, pas le chemin : un bloc deplace d un fichier a l autre reste relu.
    # C est un choix, et il est ici pour qu on le voie plutot que de le decouvrir.
    _verifie(
        "4359 registre : l empreinte fait l identite, pas le chemin",
        len(m.perimees([("aaaa", "src/main/java/Ailleurs.java", "motif")], corpus)),
        0,
    )

    # Une REINDENTATION ne doit pas invalider une lecture qui reste valable.
    bloc = ["    /// Premiere ligne.", "    /// Seconde ligne."]
    decale = ["        /// Premiere ligne.", "        /// Seconde ligne."]
    _verifie(
        "4359 registre : une reindentation ne change pas l empreinte",
        m.empreinte(bloc),
        m.empreinte(decale),
    )
    # Mais une EDITION, si : c est tout le propos.
    _verifie(
        "4359 registre : un mot change invalide l empreinte",
        m.empreinte(bloc) != m.empreinte(["    /// Premiere ligne.", "    /// Autre chose."]),
        True,
    )


def test_loupe_4359_javadoc_vieillie() -> None:
    m = _charge("loupe-4359-javadoc-vieillie.py")
    # La loupe importe desormais ses seuils du cliquet, par nature. Les fixtures surmontent une
    # classe, donc c est le seuil `type` qui les departage.
    seuil = m.SEUILS["type"]
    long_ = [f"/// Ligne {i}." for i in range(seuil + 1)]
    court = [f"/// Ligne {i}." for i in range(seuil - 1)]
    corps = ["class A {}", ""]

    # Le cas positif : bloc sous cliquet, code plus recent que lui.
    lignes = long_ + corps
    temps = [100] * len(long_) + [200, 200]
    _verifie(
        "loupe 4359 voit un bloc dont le code a bouge apres lui",
        len(m.candidats_du_fichier("A.java", lignes, temps)),
        1,
    )

    # Le code est PLUS ANCIEN : la javadoc a ete corrigee depuis, rien a signaler.
    _verifie(
        "loupe 4359 epargne un bloc plus recent que son code",
        len(m.candidats_du_fichier("A.java", lignes, [200] * len(long_) + [100, 100])),
        0,
    )

    # Meme date : c est plus de la moitie du corpus, et la loupe est aveugle a ce cas - declare.
    _verifie(
        "loupe 4359 epargne un bloc du meme commit que son code",
        len(m.candidats_du_fichier("A.java", lignes, [100] * (len(long_) + 2))),
        0,
    )

    # Sous le seuil : le bloc n est pas dans le cliquet, donc pas dans la surface de revue.
    _verifie(
        "loupe 4359 epargne un bloc court, meme avec du code plus recent",
        len(m.candidats_du_fichier("A.java", court + corps, [100] * len(court) + [200, 200])),
        0,
    )

    # Une ligne VIDE plus recente ne compte pas : sans cela un simple retour a la ligne suffirait.
    _verifie(
        "loupe 4359 ne compte pas une ligne vide comme du code",
        len(
            m.candidats_du_fichier(
                "A.java", long_ + ["", "class A {}"], [100] * len(long_) + [999, 100]
            )
        ),
        0,
    )


def test_4472_commentaire_en_corps() -> None:
    m = _charge("4472-commentaire-en-corps.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Le cas qui compte : le MEME bloc de commentaires, hors d un corps de methode puis dedans.
        # Hors d un corps, c est de l en-tete de fichier et ce cliquet n a rien a en dire ; dedans,
        # c est la population qu il tient. Un garde qui confondrait les deux compterait les licences.
        bloc = "\n".join(f"        // Ligne {i}." for i in range(m.SEUIL + 3))
        _ecrire(
            racine,
            "fr/univ_amu/iut/a/Dedans.java",
            "class Dedans {\n    void faire() {\n" + bloc + "\n        int x = 1;\n    }\n}\n",
        )
        _verifie("4472 un bloc long DANS un corps est compte", len(m.suspects(racine)), 3)

        entete = "\n".join(f"// Ligne {i}." for i in range(m.SEUIL + 3))
        _ecrire(racine, "fr/univ_amu/iut/a/Dedans.java", entete + "\nclass Dehors {}\n")
        _verifie("4472 le meme bloc HORS d un corps ne coute rien", len(m.suspects(racine)), 0)


def test_4468_javadoc_non_relue() -> None:
    m = _charge("4468-javadoc-non-relue.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "fr/univ_amu/iut/a/Lu.java", "/// Le contrat de A.\nclass Lu {}\n")
        _ecrire(racine, "fr/univ_amu/iut/a/Jamais.java", "/// Le contrat de B.\nclass Jamais {}\n")

        # Sans manifeste, TOUT est suspect : c est le premier des deux faits que le cliquet tient.
        _verifie("4468 sans manifeste, aucun fichier n est blanchi", len(m.suspects(racine, {})), 2)

        # Inscrit avec l empreinte du jour, un fichier sort de la liste.
        lu = racine / "fr/univ_amu/iut/a/Lu.java"
        # L empreinte vient du COMPTEUR, que le garde emprunte plutot que de la redire : le temoin
        # emprunte la meme, sans quoi il eprouverait une troisieme definition.
        table = {"fr/univ_amu/iut/a/Lu.java": m.empreinte(lu)}
        _verifie("4468 un fichier inscrit sort de la liste", len(m.suspects(racine, table)), 1)

        # LE SECOND FAIT, celui qui distingue ce cliquet d une case cochee : la javadoc change, donc
        # elle n a pas ete relue SOUS SA FORME ACTUELLE, et le fichier redevient suspect.
        lu.write_text("/// Le contrat de A, reecrit en douce.\nclass Lu {}\n", encoding="utf-8")
        _verifie(
            "4468 une javadoc reecrite en douce redevient suspecte",
            len(m.suspects(racine, table)),
            2,
        )


def test_4974_attente_reinventee() -> None:
    m = _charge("4974-attente-reinventee.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        sonde = "        WaitForAsyncUtils.waitFor(1, S, () -> vrai());\n"

        # Le nom ne fait pas le defaut : c est ce que la decision tient, et le harnais l eprouve
        # ici comme l auto-test l eprouve chez lui.
        _ecrire(
            racine,
            "fr/a/A.java",
            "class A {\n    private void ouvrirLaFiche() {\n" + sonde + "    }\n}\n",
        )
        _verifie("4974 une sonde privee est vue quel que soit son nom", len(m.suspects(racine)), 1)

        _ecrire(
            racine,
            "fr/a/A.java",
            "class A {\n    private void patienter() {\n" + sonde + "    }\n}\n",
        )
        _verifie("4974 renommer ne soustrait rien", m.suspects(racine), ["A.java:3"])

        # La population elargie par #4845 : une attente ecrite en clair dans un CAS DE TEST tait la
        # meme chose. La restriction aux methodes privees ne tenait qu a la facon dont le defaut
        # avait ete trouve.
        _ecrire(
            racine,
            "fr/a/T.java",
            "class T {\n    @Test\n    void un_cas() {\n" + sonde + "    }\n}\n",
        )
        _verifie("4974 une attente ecrite dans un cas de test compte", len(m.suspects(racine)), 2)

        # Le sens NEGATIF que #4845 a rendu necessaire : une CITATION en commentaire n est pas un
        # appel, et la javadoc d AttenteAvantClic en portait une.
        _ecrire(
            racine,
            "fr/a/T.java",
            "class T {\n    /// Un `WaitForAsyncUtils.waitFor(...)` nu.\n    void rien() {}\n}\n",
        )
        _verifie("4974 une citation en commentaire ne compte pas", len(m.suspects(racine)), 1)

        # Les trois sens NEGATIFS, sans lesquels un garde qui rend toutes les methodes privees
        # paraitrait juste.
        _ecrire(
            racine,
            "fr/a/B.java",
            "class B {\n    private void dormir() {\n"
            "        WaitForAsyncUtils.sleep(350, MS);\n    }\n}\n",
        )
        _verifie("4974 un sleep n est pas une attente", len(m.suspects(racine)), 1)

        _ecrire(
            racine,
            "fr/a/C.java",
            "class C {\n    private void vider() {\n"
            "        WaitForAsyncUtils.waitForFxEvents();\n    }\n}\n",
        )
        _verifie("4974 waitForFxEvents n est pas une sonde", len(m.suspects(racine)), 1)

        _ecrire(
            racine,
            "fr/a/Attente.java",
            "class Attente {\n    private static void interne() {\n" + sonde + "    }\n}\n",
        )
        _verifie("4974 l aide partagee est exemptee", len(m.suspects(racine)), 1)

        # #4997 : `waitForAsyncFx` est la meme dette sous un autre nom, et le garde le compte. Ce cas
        # vient EN DERNIER : les fichiers du dossier jetable sont partages, et un fichier ajoute plus
        # tot ferait compter un de trop a chacun des cas suivants.
        _ecrire(
            racine,
            "fr/a/F.java",
            "class F {\n    private void surFx(Runnable a) {\n"
            "        WaitForAsyncUtils.waitForAsyncFx(5_000, a);\n    }\n}\n",
        )
        _verifie("4974 waitForAsyncFx compte aussi", len(m.suspects(racine)), 2)


def test_5068_clic_sur_reference_tenue() -> None:
    m = _charge("5068-clic-sur-reference-tenue.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)

        _ecrire(
            racine,
            "fr/a/A.java",
            "class A {\n    void cas() {\n        robot.clickOn(carte);\n    }\n}\n",
        )
        _verifie("5068 un noeud deja resolu est vu", m.suspects(racine), ["A.java:3"])

        # Le PREMIER argument decide : une premiere ecriture ecartait la ligne des qu elle citait
        # `MouseButton`, et ratait un site qui tient une reference tout autant.
        _ecrire(
            racine,
            "fr/a/B.java",
            "class B {\n    void cas() {\n"
            "        robot.clickOn(carte, MouseButton.SECONDARY);\n    }\n}\n",
        )
        _verifie("5068 un second argument ne soustrait pas le site", len(m.suspects(racine)), 2)

        # Les trois sens NEGATIFS, chacun ayant fait surcompter lors des mesures de #4804.
        _ecrire(
            racine,
            "fr/a/C.java",
            'class C {\n    void cas() {\n        robot.clickOn("#champCode");\n    }\n}\n',
        )
        _verifie("5068 un selecteur litteral ne compte pas", len(m.suspects(racine)), 2)

        _ecrire(
            racine,
            "fr/a/D.java",
            'class D {\n    static final String B = "#bouton";\n'
            "    void cas() {\n        robot.clickOn(B);\n    }\n}\n",
        )
        _verifie("5068 une constante String ne compte pas", len(m.suspects(racine)), 2)

        _ecrire(
            racine,
            "fr/a/E.java",
            "class E {\n    /// `clickOn(libelle)` teleporte le pointeur.\n    void cas() {}\n}\n",
        )
        _verifie("5068 une citation en commentaire ne compte pas", len(m.suspects(racine)), 2)


def test_4475_stage_non_dimensionne() -> None:
    m = _charge("4475-stage-non-dimensionne.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Le cas qui compte : la MEME classe, avec puis sans le geste qui fait suivre le stage. Le
        # garde accepte trois formes, et `sizeToScene` est celle que `FenetreDuBanc` prescrit ici :
        # un garde qui n accepterait que `setWidth` pousserait a figer le stage, donc au defaut.
        nu = (
            "class Nu {\n    @Start\n    void start(Stage fenetre) {\n"
            "        fenetre.setScene(new Scene(racine, 980, 980));\n    }\n}\n"
        )
        _ecrire(racine, "fr/univ_amu/iut/a/NuTest.java", nu)
        _verifie(
            "4475 une scene dimensionnee sans stage dimensionne est vue", len(m.suspects(racine)), 1
        )
        _ecrire(
            racine,
            "fr/univ_amu/iut/a/NuTest.java",
            nu.replace("    }\n}", "        fenetre.sizeToScene();\n    }\n}"),
        )
        _verifie(
            "4475 sizeToScene suffit, comme FenetreDuBanc le prescrit", len(m.suspects(racine)), 0
        )


def test_4617_code_mort_et_zone_de_test() -> None:
    m = _charge("4617-code-mort-et-zone-de-test.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)

        def rapport(*violations: str) -> pathlib.Path:
            chemin = racine / "pmd.xml"
            chemin.write_text(
                '<?xml version="1.0"?><pmd version="7">' + "".join(violations) + "</pmd>",
                encoding="utf-8",
            )
            return chemin

        def fichier(chemin: str, regle: str) -> str:
            return (
                f'<file name="{chemin}"><violation beginline="1" rule="{regle}">x</violation>'
                "</file>"
            )

        prod = "/depot/src/main/java/fr/univ_amu/iut/A.java"
        test = "/depot/src/test/java/fr/univ_amu/iut/ATest.java"

        # Le cas de #4554 : une methode morte compte, dans les DEUX zones.
        _verifie(
            "4617 le code mort compte en production",
            len(m.suspects(rapport(fichier(prod, "UnusedPrivateMethod")))),
            1,
        )
        _verifie(
            "4617 le code mort compte aussi dans la zone de test",
            len(m.suspects(rapport(fichier(test, "UnusedPrivateMethod")))),
            1,
        )

        # Le controle qui porte la decision : repeter un litteral est ce qu un test DOIT faire, et
        # cette seule regle rend 1 366 des 1 428 signalements du depot. Sans cette tolerance, le
        # cliquet serait illisible ; avec elle appliquee partout, la production perdrait une regle
        # qu elle tient a zero. Les deux bords sont donc exiges.
        _verifie(
            "4617 un litteral repete est tolere dans la zone de test",
            len(m.suspects(rapport(fichier(test, "AvoidDuplicateLiterals")))),
            0,
        )
        _verifie(
            "4617 le meme litteral repete compte en production",
            len(m.suspects(rapport(fichier(prod, "AvoidDuplicateLiterals")))),
            1,
        )

        # LA COMPENSATION : une violation de plus en production, une de moins en test. Le total ne
        # bouge pas. Un compteur unique reste donc vert pendant qu'une regression passe dans la zone
        # qui compte le plus - le defaut que l'ADR 4587 refuse, « surtout pas un seul sur les deux ».
        avant = m.suspects(
            rapport(
                fichier(prod, "UnusedPrivateMethod"),
                fichier(test, "NcssCount"),
                fichier(test, "GodClass"),
            )
        )
        apres = m.suspects(
            rapport(
                fichier(prod, "UnusedPrivateMethod"),
                fichier(prod, "NcssCount"),
                fichier(test, "GodClass"),
            )
        )
        _verifie("4617 la compensation ne change pas le total", len(avant), len(apres))
        # ET c'est pourquoi le compte se fait PAR ZONE : a total constant, la production gagne une
        # violation. Un compteur unique resterait vert ; deux compteurs disjoints rougissent.
        prod_avant = m.suspects(
            rapport(
                fichier(prod, "UnusedPrivateMethod"),
                fichier(test, "NcssCount"),
                fichier(test, "GodClass"),
            ),
            zone="production",
        )
        prod_apres = m.suspects(
            rapport(
                fichier(prod, "UnusedPrivateMethod"),
                fichier(prod, "NcssCount"),
                fichier(test, "GodClass"),
            ),
            zone="production",
        )
        _verifie(
            "4682 la zone de production, elle, voit la regression",
            len(prod_apres),
            len(prod_avant) + 1,
        )
        _verifie(
            "4682 et la zone de test voit sa baisse",
            len(
                m.suspects(
                    rapport(
                        fichier(prod, "UnusedPrivateMethod"),
                        fichier(prod, "NcssCount"),
                        fichier(test, "GodClass"),
                    ),
                    zone="test",
                )
            ),
            1,
        )

        # Un garde qui ne sait pas lire REFUSE. Rendre zero sur un rapport absent le rendrait vert
        # au moment precis ou il sert - le defaut de #4544 sous une autre forme.
        absent = racine / "jamais-produit.xml"
        try:
            m.suspects(absent)
            _verifie("4617 un rapport absent fait REFUSER", 0, 1)
        except SystemExit:
            _verifie("4617 un rapport absent fait REFUSER", 1, 1)


def test_4476_javadoc_raconte_son_extraction() -> None:
    m = _charge("4476-javadoc-raconte-son-extraction.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        # Le cliquet ne vise pas toute mention d extraction, mais celle qui nomme l OUTIL qui l a
        # exigee : c est ce voisinage-la qui fait le recit, « on a scinde parce que PMD a rougi ».
        _ecrire(
            racine,
            "fr/univ_amu/iut/a/Recit.java",
            "/// Collaborateur extrait de ServiceImport (plafond NcssCount).\nclass Recit {}\n",
        )
        _verifie("4476 un recit qui nomme l outil qui l a exige est vu", len(m.suspects(racine)), 1)
        # LA BORNE QUI COMPTE : le verbe et l outil dans DEUX phrases distinctes sont une mention
        # legitime. Un cliquet qui elargirait le voisinage au bloc entier les compterait, et sa liste
        # grossirait sans qu une javadoc ait change.
        _ecrire(
            racine,
            "fr/univ_amu/iut/a/Recit.java",
            "/// Ce que la classe garantit.\n///\n/// Voir ServiceImport pour le parcours complet.\nclass Recit {}\n",
        )
        _verifie(
            "4476 le nom de l outil seul, hors du verbe, est epargne", len(m.suspects(racine)), 0
        )


def test_4477_longueur_des_adr() -> None:
    """Le temoin d origine n affirmait que `isinstance(suspects(), list)`.

    Un garde qui aurait cesse de detecter le passait, ce qui est exactement le faux vert que cette
    suite existe pour interdire - et la phrase de cloture affirmait pourtant que les scripts
    detectent leur violation temoin. Trouve par mutation en passe 6 : neutraliser la detection ne
    faisait pas rougir la suite.

    Quatre fixtures pour quatre comportements, et un compte EXACT de 1. Ce que le garde EPARGNE est
    ce qui se casse : un en-tete OKF bavard ou un encart de revision long feraient rougir une ADR
    dont la DECISION tient en trois lignes, et c est la decision que le seuil borne.
    """
    m = _charge("4477-longueur-des-adr.py")
    entete = '---\ntype: adr\ntitle: "Une decision"\n---\n'
    long_ = " ".join(f"mot{i}" for i in range(m.SEUIL + 100))
    court = " ".join(f"mot{i}" for i in range(100))
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(racine, "0001-longue.md", entete + "# Titre\n\n" + long_ + "\n")
        _ecrire(racine, "0002-courte.md", entete + "# Titre\n\n" + court + "\n")
        # L encart de revision est retire AVANT la mesure : ses lignes sont indentees de quatre
        # espaces, et l admonition s arrete a la premiere ligne non indentee.
        encart = m.ENCART + "\n" + "\n".join("    " + l for l in long_.split(" "))
        _ecrire(racine, "0003-encart.md", entete + "# Titre\n\n" + encart + "\n\n" + court + "\n")
        # Le sommaire et le journal sont reserves : ils ne portent pas de decision.
        _ecrire(racine, "index.md", entete + "# Sommaire\n\n" + long_ + "\n")
        n = len(m.suspects(racine=racine))
        _verifie(
            "4477 compte l ADR longue, epargne la courte, l encart de revision et le sommaire",
            n,
            1,
        )


# Le motif d un import qui declare le corpus. `RACINES` et `RACINES_ANCREES` disent « les deux
# arbres » ; `PRODUCTION` seule dit « la production, et voici pourquoi » dans le fichier qui
# l importe.
DECLARE_DEUX_ARBRES = re.compile(r"^from _commun import (.+)$", re.M)
DEUX_ARBRES = re.compile(r"\bRACINES(?:_ANCREES)?\b")


def gardes_deux_arbres() -> list[str]:
    """Les gardes qui DECLARENT lire les deux arbres, derives et non enumeres (ADR 4586).

    La forme precedente etait une liste ecrite a la main, et elle avait derive : dix entrees pour
    quinze gardes portant le chemin de l arbre de test. Six lisaient donc les deux arbres sans que
    rien ne le verifie, et l un d eux pouvait cesser d en lire un - son compte aurait baisse, et un
    cliquet ne se plaint pas qu on lui retire du corpus.

    Le depot connaissait deja le piege : `verifie_temoins_non_decoratifs.py` derive sa liste au lieu
    de l enumerer, parce qu un garde neuf passerait au travers. La lecon vaut ici.

    Ce qui rend la derivation fiable est le refus de `verifie_corpus_declare.py` : sans lui, un garde
    neuf reecrirait le chemin en clair et redeviendrait invisible a cette liste.
    """
    trouves = []
    for source in sorted(ICI.glob("*.py")):
        if source.name == "_commun.py":
            continue
        imports = DECLARE_DEUX_ARBRES.search(source.read_text(encoding="utf-8"))
        if imports and DEUX_ARBRES.search(imports.group(1)):
            trouves.append(source.name)
    return trouves


# Les trois aides qui rendent un verdict, et le premier argument que chacune attend : le NUMERO de
# l ADR. Le rapport lit ses verdicts sur ce numero ; un slug y passerait sans rougir et sa ligne
# serait jetee en silence.
REND_UN_VERDICT = re.compile(r"\b(rapporte|rapporte_plancher|loupe)\(\s*([^,\s)]+)", re.M)


def test_loupe_4472_densite_de_commentaire() -> None:
    """La loupe de densite compte le commentaire contre le code, et n a jamais eu de temoin.

    Portee depuis la ligne d origine, elle n etait chargee par personne : sa detection pouvait avoir
    cesse sans que rien ne le dise, et son verdict etait de surcroit jete par le rapport (#4635).
    """
    m = _charge("loupe-4472-densite-de-commentaire.py")
    with tempfile.TemporaryDirectory() as d:
        racine = pathlib.Path(d)
        _ecrire(
            racine,
            "Bavarde.java",
            "class Bavarde {\n"
            "  void faire() {\n"
            + "    // une ligne de commentaire\n" * 12
            + "".join(f"    int x{i} = {i};\n" for i in range(6))
            + "  }\n"
            "}\n",
        )
        _ecrire(
            racine,
            "Sobre.java",
            "class Sobre {\n  void faire() {\n"
            + "".join(f"    int y{i} = {i};\n" for i in range(6))
            + "  }\n}\n",
        )
        bavardes = [nom for _, nom, _, _ in m.methodes(racines=[racine])]
        _verifie("loupe 4472 voit la methode bavarde", any("Bavarde" in n for n in bavardes), True)
        _verifie("loupe 4472 laisse la methode sobre", any("Sobre" in n for n in bavardes), False)


def test_un_verdict_se_rend_sur_le_numero_de_son_adr() -> None:
    """Le premier argument d une aide de verdict est un numero, jamais un slug (issue #4635).

    `loupe-4472-densite-de-commentaire.py` passait le litteral « densite-de-commentaire », la
    convention du depot d origine restee dans sa ligne de verdict au moment du portage. Le script
    tournait, trouvait 43 candidats, et `rapport.py` les jetait : sa ligne ne correspondait a aucun
    de ses motifs, et rien ne disait qu elle manquait.

    Ce cas est STATIQUE et ne lance rien : il lit l appel. Relancer les vingt scripts couterait le
    temps du rapport entier pour attraper une faute de frappe.
    """
    fautifs = []
    for source in sorted(ICI.glob("*.py")):
        if source.name in ("_commun.py", ICI_NOM):
            continue
        for appel in REND_UN_VERDICT.finditer(source.read_text(encoding="utf-8")):
            aide, premier = appel.group(1), appel.group(2)
            if premier[:1] in ("'", '"') and not premier.strip("\"'").isdigit():
                fautifs.append(f"{source.name} : {aide}({premier}…)")
    _verifie("un verdict se rend sur le NUMERO de son ADR, jamais sur un slug", fautifs, [])


def test_les_gardes_de_code_lisent_les_deux_arbres() -> None:
    """Ce cas tient le CORPUS, la ou le temoin propre a chaque garde tient sa DETECTION.

    Les deux se cassent separement : un garde peut continuer de detecter parfaitement sur l arbre
    qu on lui laisse, et avoir cesse de lire l autre. Rien ne le montrerait, puisque le compte
    baisserait sans jamais rougir.

    `2635-refus-sans-surface.py` et `4395-renvois-en-javadoc.py` n y sont pas, et ne s excluent plus
    par une liste : ils importent `PRODUCTION` seule, et disent dans leur propre en-tete pourquoi.
    Pour le premier c est une exception justifiee - le test qui prouve l ADR 2635 doit citer le
    glyphe du menu, et l y etendre interdirait aux tests d affirmer les chaines que la regle produit.
    Pour le second c est un trou connu, mesure a 976 renvois, et suivi par #4587.
    """
    derives = gardes_deux_arbres()
    _verifie(
        "la liste des gardes a deux arbres se derive, et n est pas vide", len(derives) > 10, True
    )
    for nom in derives:
        m = _charge(nom)
        # Le nom de l attribut varie : `RACINES` chez la plupart, `ARBRES` chez les loupes bornees a
        # un paquet et chez les gardes qui composent leurs zones.
        racines = getattr(m, "RACINES", None) or getattr(m, "ARBRES", ())
        # Le chemin CONTIENT `src/test/java`, il ne s y termine pas forcement : une loupe peut etre
        # bornee a un paquet, et son sous-arbre de test est un corpus de test tout autant.
        _verifie(
            f"{nom.removesuffix('.py')} : l arbre de test est dans son corpus",
            any("test" in str(r) for r in racines),
            True,
        )


def test_un_plancher_perime_refuse() -> None:
    """Un plancher qui SAIT compter et ne sait pas REFUSER ne garde rien (issue #4683).

    Les deux gardes de plancher sortaient en 0 des que la mesure depassait le seuil. Le message
    disait pourtant quoi faire - « relevez-le, sinon ce qui vient d etre gagne se reperdra » - mais
    personne ne lit une sortie verte. Les deux planchers etaient perimes de vingt-et-un renvois
    moins de vingt-quatre heures apres avoir ete poses.

    Le seuil vient d une ADR reelle : `plancher()` le lit dans son en-tete, et une fixture
    demanderait de fabriquer une ADR pour eprouver deux comparaisons d entiers.
    """
    commun = _charge("_commun.py")
    seuil = commun.plancher("4395")
    codes = {}
    for nom, mesure in (("perte", seuil - 1), ("ok", seuil), ("a-relever", seuil + 1)):
        codes[nom] = commun.rapporte_plancher("4395", "temoin de polarite", mesure, "renvois")
    _verifie("un plancher perdu refuse", codes["perte"], 1)
    _verifie("un plancher tenu passe", codes["ok"], 0)
    _verifie("un plancher perime refuse aussi", codes["a-relever"], 1)


def test_une_population_vide_refuse() -> None:
    """Un garde qui n a rien LU rend zero suspect, et ce zero ressemble a un succes (issue #5007).

    Le mode de panne se mesure : sur 98 commits qui deplacent un seuil d ADR, 28 sont des
    CORRECTIFS. Un ciblage manque rend un nombre plausible que personne ne questionne, justement
    parce qu il ne bouge pas ; le jour ou le ciblage se corrige, le saut se lit comme une
    regression alors que c est la premiere mesure juste.

    Compter ce qu on a LU a cote de ce qu on a RETENU rend la difference visible, et le zero absolu
    refuse. Le cliquet de l ADR 0008 vaut 0 : sans le champ `lus`, zero suspect y passe en vert,
    ce qui est exactement le faux vert a fermer.

    Le troisieme cas fixe la semantique de la migration : une population NON DECLAREE ne refuse
    pas. Sans lui, les 36 sites d appel casseraient d un coup, et le cliquet qui les fait descendre
    n aurait pas de place ou vivre.
    """
    commun = _charge("_commun.py")
    _verifie(
        "une population vide refuse", commun.rapporte("0008", "temoin de population", [], lus=0), 1
    )
    _verifie(
        "une population lue passe", commun.rapporte("0008", "temoin de population", [], lus=851), 0
    )
    _verifie(
        "une population non declaree ne refuse pas encore",
        commun.rapporte("0008", "temoin de population", []),
        0,
    )


def test_une_loupe_muette_le_dit_sans_bloquer() -> None:
    """Une loupe qui n a rien lu rend « aucun candidat », ce qui se lit « rien a revoir » (#5007).

    C est le faux vert sous sa forme de loupe. Mais une loupe ne BLOQUE jamais : c est sa definition
    dans l ADR 2465, et la changer serait une autre decision que celle de ce lot. Elle DIT donc
    qu elle n a rien lu, et rend toujours 0 ; c est le manifeste qui comptera ce silence.
    """
    import contextlib
    import io

    commun = _charge("_commun.py")
    sortie = io.StringIO()
    with contextlib.redirect_stdout(sortie):
        code = commun.loupe("0020", "temoin de population", [], lus=0)
    rendu = sortie.getvalue()
    _verifie("une loupe muette ne bloque pas", code, 0)
    _verifie("une loupe muette le dit", "population-vide" in rendu, True)
    _verifie("une loupe qui a lu porte son compte", "lus=12" in _rendu_loupe(commun, 12), True)


def _rendu_loupe(commun, lus: int) -> str:
    """La sortie d une loupe, capturee, pour que le cas voisin lise ce qu elle ecrit."""
    import contextlib
    import io

    sortie = io.StringIO()
    with contextlib.redirect_stdout(sortie):
        commun.loupe("0020", "temoin de population", ["un candidat"], lus=lus)
    return sortie.getvalue()


def test_un_plancher_sans_population_dit_pourquoi() -> None:
    """Un plancher sur population vide refuse deja, mais pour la mauvaise raison (#5007).

    Il mesure 0, tombe sous son seuil, et annonce une PERTE. Le message envoie alors chercher des
    renvois disparus, alors que rien n a ete lu. Un refus qui accuse la mauvaise cause coute une
    enquete, et c est le defaut que l ADR 4002 nomme sur un autre axe.
    """
    import contextlib
    import io

    commun = _charge("_commun.py")
    sortie = io.StringIO()
    with contextlib.redirect_stdout(sortie):
        code = commun.rapporte_plancher("4395", "temoin de population", 0, "renvois", lus=0)
    _verifie("un plancher sans population refuse", code, 1)
    _verifie("et il dit que rien n a ete lu", "population-vide" in sortie.getvalue(), True)


def test_le_rapport_lit_encore_les_trois_lignes() -> None:
    """La COUTURE entre ce que `_commun` ECRIT et ce que `rapport.py` LIT (issue #5007).

    Les trois motifs de `rapport.py` sont ancres `^...$` sur l ordre exact des champs. Ajouter `lus`
    entre `ADR NNNN` et `suspects=` les a tous les trois rendus aveugles, et la panne est SILENCIEUSE :
    le rapport ne rate pas, il declare simplement tous les gardes MUETS. C est le mode de panne que
    ce lot existe pour rendre visible, produit par le lot lui-meme.

    Le voisin `test_resserre_cliquets_appelle_le_rapport` garde la meme famille de defaut sur une
    autre couture, et sa docstring dit pourquoi : les deux se parlent encore, ou ils ne se parlent
    plus sans qu un seul cas ne rougisse.
    """
    import contextlib
    import io

    commun = _charge("_commun.py")
    rapport = _charge("rapport.py")

    def rendu(appel) -> str:
        sortie = io.StringIO()
        with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(io.StringIO()):
            appel()
        return sortie.getvalue()

    # On verifie les VALEURS capturees, et non le seul appariement : un groupe capturant ajoute pour
    # `lus` decalerait les indices, `rapport.py` lirait le mauvais nombre, et un cas qui ne teste que
    # « ca apparie » resterait vert. C est le meme defaut, un cran plus fin.
    #
    # Ce cas a fait exactement cela le 2026-09-01 : quand `lus` est devenu capturant (#5053), il a
    # rougi sur les trois motifs a la fois, en disant a chaque fois quelle valeur il obtenait a la
    # place. Les indices ci-dessous ont donc bouge d un cran, et le compte LU est desormais verifie
    # lui aussi : ce que le motif capture doit etre ce qu on croit, pas seulement quelque chose.
    cliquet = rapport.LIGNE_CLIQUET.search(
        rendu(lambda: commun.rapporte("0008", "temoin de couture", [], lus=2076))
    )
    _verifie("le rapport lit une ligne de cliquet", bool(cliquet), True)
    _verifie("et il en tire le bon numero", cliquet.group(1) if cliquet else None, "0008")
    _verifie("et le compte LU", cliquet.group(2) if cliquet else None, "2076")
    _verifie("et le bon compte de suspects", cliquet.group(3) if cliquet else None, "0")
    _verifie("et le bon verdict", cliquet.group(5) if cliquet else None, "ok")

    loupe = rapport.LIGNE_LOUPE.search(
        rendu(lambda: commun.loupe("0020", "temoin de couture", ["x"], lus=9))
    )
    _verifie("le rapport lit une ligne de loupe", bool(loupe), True)
    _verifie("et il en tire le compte LU", loupe.group(2) if loupe else None, "9")
    _verifie("et le bon compte de candidats", loupe.group(3) if loupe else None, "1")

    plancher = rapport.LIGNE_PLANCHER.search(
        rendu(
            lambda: commun.rapporte_plancher("4395", "temoin de couture", 3245, "renvois", lus=4026)
        )
    )
    _verifie("le rapport lit une ligne de plancher", bool(plancher), True)
    _verifie("et il en tire le compte LU", plancher.group(2) if plancher else None, "4026")
    _verifie("et la bonne mesure", plancher.group(3) if plancher else None, "3245")

    _verifie(
        "et il les lit encore quand le compte n est pas declare",
        bool(
            rapport.LIGNE_CLIQUET.search(
                rendu(lambda: commun.rapporte("0008", "temoin de couture", []))
            )
        ),
        True,
    )


def test_le_contrat_a_une_forme_et_refuse_l_incomplet() -> None:
    """Le format du contrat, ecrit une fois et eprouve ici (issue #5009).

    Un garde ne declare rien de ce qu il est, donc `contrats-des-gardes.py` doit DEVINER : sur les 33
    gardes qu il lit, 11 ne rendent aucune population, 6 aucune ADR, 6 aucun verdict normalise. Le
    contrat remplace la devinette par une declaration, et cette fonction en porte la forme.

    Le cas de REFUS compte autant que les autres : un contrat auquel il manque un champ doit refuser,
    sans quoi le deriveur lirait un contrat partiel en le prenant pour complet.
    """
    import contextlib
    import io

    commun = _charge("_commun.py")
    complet = {
        "geste": "echec silencieux : catch au corps vide",
        "population": "PRODUCTION + TESTS",
        "dispositif": "cliquet",
        "seuil": "0, polarite=descend",
        "temoin": "scripts/adr/verifie_scripts.py#test_0008_echec_silencieux",
        "decision": "ADR 0008",
    }

    sortie = io.StringIO()
    with contextlib.redirect_stdout(sortie):
        code = commun.imprime_contrat("scripts/adr/0008-echec-silencieux.py", complet)
    rendu = sortie.getvalue()

    _verifie("un contrat complet passe", code, 0)
    _verifie(
        "il porte sa ligne d en-tete",
        "CONTRAT | garde=scripts/adr/0008-echec-silencieux.py" in rendu,
        True,
    )
    _verifie("et ses six champs", all(f"{c}: " in rendu for c in complet), True)
    # Les champs se lisent DANS L ORDRE declare : un deriveur qui les apparie par position, ou un
    # humain qui compare deux contrats cote a cote, dependent de cet ordre.
    _verifie(
        "dans l ordre",
        [l.split(":")[0] for l in rendu.splitlines() if ": " in l and not l.startswith("CONTRAT")],
        list(complet),
    )

    # Le sens NEGATIF, sans lequel un imprimeur qui accepterait TOUT passerait les cas precedents.
    for manquant in ("population", "temoin"):
        ampute = {k: v for k, v in complet.items() if k != manquant}
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            code = commun.imprime_contrat("scripts/adr/0008-echec-silencieux.py", ampute)
        _verifie(f"un contrat sans « {manquant} » refuse", code, 1)


def test_le_refus_s_eprouve_sur_un_garde_reel() -> None:
    """Le refus sur population vide, eprouve sur un garde REEL de chacune des quatre familles.

    `test_une_population_vide_refuse` eprouve `_commun` : il lui PASSE zero et regarde le code. Il ne
    dit pas qu un garde donne, dont on vide l arbre, ARRIVE a zero. Entre les deux il y a tout ce que
    la conversion a introduit, et le lot #5051 a montre que ces divergences sont reelles : quatre
    formes differentes sur trente-trois appels, dont une signature sans defaut et une normalisation
    ecrite DANS la fonction.

    Ce cas eprouve donc les DEUX moities : que la population du garde tombe a zero quand on la vide,
    et que le verdict bati dessus refuse.

    « Refuser » ne veut pas dire la meme chose partout, et c est mesure : `rapporte` et
    `rapporte_plancher` rendent 1, une loupe rend 0 et le DIT. Une loupe ne bloque jamais (ADR 2465),
    donc un cas qui attendrait 1 de l une serait faux, et vert pour la mauvaise raison.

    `compte-les-reliquats` est absent expres : sa population vide est sa REUSSITE, il ne declare pas
    `lus` pour cette raison, et l eprouver ici contredirait une decision ecrite (#5015).
    """
    import contextlib
    import io

    commun = _charge("_commun.py")

    def verdict(appel) -> tuple[int, str]:
        """Le code rendu ET ce qui a ete imprime : une loupe se juge sur sa ligne, pas sur son code."""
        sortie = io.StringIO()
        with contextlib.redirect_stdout(sortie), contextlib.redirect_stderr(io.StringIO()):
            code = appel()
        return code, sortie.getvalue()

    with tempfile.TemporaryDirectory() as brut:
        vide = pathlib.Path(brut)

        # 1. Les FICHIERS d un arbre. La famille des vingt-cinq.
        fichiers = _charge("0008-echec-silencieux.py")
        # Le CONTRASTE d abord. Un cas qui n affirmerait que des vides serait decoratif : la
        # neutralisation de `verifie_temoins_non_decoratifs.py` remplace chaque fonction par
        # `lambda: []`, si bien que « la population est vide » resterait vrai sur un garde mort.
        # Ce que l on veut savoir est que la population DISCRIMINE, pas qu elle sait rendre zero.
        _verifie("0008 sur le depot lit des fichiers", len(fichiers.fichiers()) > 0, True)
        _verifie("0008 sur un arbre vide n a rien lu", len(fichiers.fichiers(vide)), 0)
        code, _ = verdict(
            lambda: commun.rapporte(
                "0008",
                "temoin de famille",
                fichiers.suspects(vide),
                lus=len(fichiers.fichiers(vide)),
            )
        )
        _verifie("et son verdict REFUSE", code, 1)

        # 2. Les ZONES d un rapport. Le compte vient de l arbre vise, pas des entrees du rapport,
        #    et c est pourquoi cette zone-ci est videable depuis #5054.
        zones = _charge("4617-code-mort-et-zone-de-test.py")
        _verifie("4617 sur le depot lit sa zone", len(zones.fichiers(zone="production")) > 0, True)
        _verifie("4617 sur une zone vide n a rien lu", len(zones.fichiers(vide, "production")), 0)
        faux = vide / "pmd.xml"
        faux.write_text('<?xml version="1.0"?><pmd></pmd>', encoding="utf-8")
        code, _ = verdict(
            lambda: commun.rapporte(
                "4682",
                "temoin de famille",
                zones.suspects(faux, "production"),
                lus=len(zones.fichiers(vide, "production")),
            )
        )
        _verifie("et son verdict REFUSE", code, 1)

        # 3. La FORGE. L unite est l issue, et une demande qui ne rend rien est le mode de panne.
        forge = _charge("loupe-4712-lots-multi-pr.py")
        # `lus` se DERIVE de la population vidée, et ne s ecrit pas `0` en dur : ecrit en dur, il
        # tiendrait meme si le garde cessait de compter ses issues, et le cas serait vert pour une
        # raison qui n a rien a voir avec la forge.
        corps_d_epic = (
            "- [x] **Lot 0 - Instruction.** Fait.\n"
            "- [ ] **Lot 1 - Porter.** Sous-chantier #99, parce qu il porte deux issues\n"
            "      et au moins deux PR.\n"
        )
        _verifie("4712 sur un corps d EPIC voit ses lots", len(forge.lots(corps_d_epic)), 2)
        sans_issue: list[dict] = []
        _verifie("4712 sans aucune issue n a rien lu", len(forge.rapport(sans_issue)), 0)
        code, dit = verdict(
            lambda: commun.loupe(
                "4712", "temoin de famille", forge.rapport(sans_issue), lus=len(sans_issue)
            )
        )
        _verifie("et la loupe le DIT", "population-vide" in dit, True)
        _verifie("sans bloquer, parce qu une loupe ne bloque jamais", code, 0)

        # 4. Les gardes MUTES. L unite est le garde, et zero garde mute est un harnais qui dort.
        temoins = _charge("verifie_temoins_non_decoratifs.py")
        _verifie(
            "les temoins voient les gardes que la suite charge", len(temoins.mutes()) > 0, True
        )
        _verifie("les temoins sans garde a muter n ont rien lu", len(temoins.mutes([])), 0)
        # Les suspects sont passes VIDES plutot que par `temoins.suspects([])`, et ce n est pas de
        # la paresse : `suspects()` mute chaque garde de `mutes()` en relancant la suite entiere.
        # Sur une population vide il ne coute rien, mais le jour ou l on MUTE ce garde pour verifier
        # que ce cas-ci attrape, `mutes()` rend les 27 gardes et l epreuve part pour dix minutes.
        # Ce que ce cas doit prouver est que la population tombe a zero, ce que la ligne au-dessus
        # dit deja ; le verdict, lui, se juge sur le compte.
        code, _ = verdict(
            lambda: commun.rapporte("4490", "temoin de famille", [], lus=len(temoins.mutes([])))
        )
        _verifie("et son verdict REFUSE", code, 1)


def test_resserre_cliquets_appelle_le_rapport() -> None:
    """La COUTURE entre les deux modules, la ou le temoin voisin n eprouvait que leurs pieces.

    `test_rapport_et_resserrement` verifie le motif de `rapport.py` et la detection de
    `resserre_cliquets.py`, chacun de son cote. Aucun des deux n appelait l autre, et le jour ou
    `collecter()` est passe de deux listes a quatre (#4635), l appel a casse sans qu un seul cas ne
    rougisse. Le harnais chargeait pourtant les deux fichiers.

    Ce cas ne mesure rien d autre que cela : les deux se parlent encore.
    """
    resserre = _charge("resserre_cliquets.py")
    rapport = _charge("rapport.py")
    attendus = rapport.collecter()
    signature = resserre.__doc__ is not None
    _verifie("resserre_cliquets se charge a cote de rapport", signature, True)
    _verifie("collecter() rend le nombre de listes que resserre_cliquets deballe", len(attendus), 4)


def test_rapport_et_resserrement() -> None:
    rapport = _charge("rapport.py")
    # Le parsing : une ligne normalisée doit être reconnue.
    #
    # Ce littéral épingle le format SUR LE FIL, et c'est pour cela qu'il ne se remplace pas par un
    # appel à `_commun` (issue #5007). Le cas voisin `test_le_rapport_lit_encore_les_trois_lignes`
    # fait produire la ligne par `_commun` et la fait lire par `rapport.py` : il attrape une dérive
    # d'UN côté, jamais une dérive des DEUX, qui resterait verte. Le littéral, lui, ne bouge que si
    # quelqu'un décide de changer le format, et le fait alors sciemment.
    ligne = "ADR 0099 | lus=42 | suspects=2 | cliquet=5 | verdict=a-resserrer"
    trouve = rapport.LIGNE_CLIQUET.search(ligne)
    _verifie("rapport.py parse une ligne de cliquet", bool(trouve), True)
    _verifie(
        "et il en tire les bons champs, le compte lu compris",
        trouve.groups() if trouve else None,
        ("0099", "42", "2", "5", "a-resserrer"),
    )
    # La seconde forme acceptee : un garde qui ne declare pas encore son compte rend `lus=?`.
    _verifie(
        "rapport.py parse un compte non declare",
        bool(
            rapport.LIGNE_CLIQUET.search(
                "ADR 0099 | lus=? | suspects=2 | cliquet=5 | verdict=a-resserrer"
            )
        ),
        True,
    )
    # La détection de resserrement : cliquet 5 pour 2 suspects -> ramener à 2.
    #
    # Les tuples sont ecrits EN DUR, et c est ce qui tient l arite. Quand `lus` y a ete ajoute
    # (#5053), ce cas a leve « expected 5, got 4 » avant qu une ligne de rendu ne soit ecrite : la
    # note d origine de `rapport.py` craignait qu un decalage passe en silence, et il ne passe plus.
    props = rapport.resserrements([("0099", "42", 2, 5, "a-resserrer")])
    _verifie("rapport.py propose de resserrer 5 -> 2", props, [("0099", 2)])
    # Aucune proposition quand la marge colle.
    props2 = rapport.resserrements([("0099", "42", 5, 5, "ok")])
    _verifie("rapport.py ne resserre pas une marge exacte", props2, [])

    # Le compte doit traverser jusqu au RENDU, et pas seulement jusqu au tuple. C est la moitie du
    # trajet qu aucun cas ne couvrait : `lus` pouvait etre capture, range, puis jete a l affichage.
    rendu_texte = rapport.rendre([("0099", "42", 2, 5, "a-resserrer")], [], [], [], markdown=False)
    _verifie("le rendu texte porte le compte lu", "lus=42" in rendu_texte, True)
    rendu_md = rapport.rendre([("0099", "?", 2, 5, "ok")], [], [], [], markdown=True)
    _verifie("le rendu markdown porte un compte non declare", "| ? |" in rendu_md, True)
    rendu_loupe = rapport.rendre([], [], [("4472", "2080", 43)], [], markdown=False)
    _verifie("le rendu d une loupe porte ce qu elle a lu", "sur 2080" in rendu_loupe, True)

    # MESURER n est pas RESORBER, et le confondre a laisse trois chiffres perimes en une journee
    # (#4469). Une valeur qui MONTE ou qui ne bouge pas ne passe par aucun resserrement : la passe
    # d alignement doit malgre tout reposer les balises sur ce que l en-tete declare.
    resserre = _charge("resserre_cliquets.py")
    _verifie(
        "resserre_cliquets expose une passe d alignement, distincte du resserrement",
        callable(getattr(resserre, "aligner_les_balises", None)),
        True,
    )
    _verifie(
        "l alignement lit `ratchet:` comme `floor:` - les deux polarites vivent en balise",
        bool(resserre.SEUIL_DECLARE.search("floor: 7\n"))
        and bool(resserre.SEUIL_DECLARE.search("ratchet: 7\n")),
        True,
    )


def _completude(dossier: pathlib.Path | None = None, charges: set[str] | None = None) -> list[str]:
    """Les detecteurs que l'etape « Cliquets ADR » lance et que ce harnais n'exerce pas.

    L'etape lance `scripts/adr/[0-9]*.py`. Ce harnais existe pour prouver que chacun DETECTE
    encore : sans ce controle, un detecteur ajoute sans cas temoin n'est tenu que par son cliquet,
    c'est-a-dire par un compte qui ne monte pas - le trou exact que l'en-tete de ce fichier decrit.
    Mesure du 2026-08-23 : `2635-refus-sans-surface.py` et `3947-message-enveloppe.py` etaient dans
    ce cas, et rien ne pouvait le dire (#4268).
    """
    dossier = ICI if dossier is None else dossier
    charges = _charges if charges is None else charges
    detecteurs = {p.name for p in dossier.glob("[0-9]*.py")}
    if not detecteurs:
        raise SystemExit(
            f"Aucun detecteur sous {dossier} : c'est le HARNAIS qui est en cause, pas les scripts. "
            "Le dossier a-t-il ete deplace ?"
        )
    return sorted(detecteurs - charges)


def auto_test() -> int:
    """Teste le testeur.

    Ce fichier prouve que chaque detecteur detecte encore. Rien ne prouvait que LUI le fasse : un
    `_completude` qui aurait cesse de comparer rendrait une liste vide, c'est-a-dire un vert, sur un
    dossier ou un detecteur n'a aucun cas. C'est la forme meme du defaut que tout ce mecanisme
    combat, un cran plus haut (#4268).

    Repond a `--auto-test` comme les 30 autres gardes du depot, et pour la meme raison.

    ⚠️ Ce qu'il ne couvre PAS, et il vaut mieux le lire ici que le croire : il eprouve la
    FONCTION `_completude`, pas son cablage. Debrancher son appel plus bas rendrait le harnais
    vert sans que ces trois cas bronchent - mesure faite. Fermer ce trou demanderait de rendre le
    corps de `__main__` appelable avec un dossier injecte, ce qui ferait rejouer les quatorze cas
    reels a chaque essai. Le cout a paru superieur au risque : l'appel tient en une ligne, juste
    sous les cas, et se voit a la relecture.
    """
    echecs: list[str] = []

    def verifie(cas: str, obtenu, attendu) -> None:
        if obtenu == attendu:
            print(f"  ✔ {cas}")
        else:
            echecs.append(cas)
            print(f"  ✘ {cas} : attendu {attendu}, obtenu {obtenu}")

    print("Auto-test du harnais lui-même (#4268) :")
    with tempfile.TemporaryDirectory() as d:
        faux = pathlib.Path(d)
        (faux / "1234-temoin.py").write_text("", encoding="utf-8")
        # `rapport.py` ne commence pas par un chiffre : l'etape « Cliquets ADR » ne le lance pas,
        # le controle n'a donc pas a l'exiger. Sans ce cas, un controle trop large passerait.
        (faux / "rapport.py").write_text("", encoding="utf-8")

        verifie(
            "un détecteur sans cas témoin est signalé",
            _completude(faux, set()),
            ["1234-temoin.py"],
        )
        verifie(
            "le même, une fois exercé, ne l'est plus",
            _completude(faux, {"1234-temoin.py"}),
            [],
        )

    # Un dossier sans detecteur accuse le HARNAIS : il ne rend pas un vert rassurant. C'est le
    # patron des autres gardes du depot - distinguer « rien a redire » de « je n'ai rien lu ».
    with tempfile.TemporaryDirectory() as d:
        try:
            _completude(pathlib.Path(d), set())
            accuse = False
        except SystemExit:
            accuse = True
    verifie("un dossier sans détecteur accuse le harnais, au lieu de rendre un vert", accuse, True)

    print("\n3 cas, dont 1 qui DOIT rougir sur un harnais aveugle.")
    if echecs:
        print(
            f"{len(echecs)} cas en échec : ne pas se fier au verdict de ce harnais.",
            file=sys.stderr,
        )
        return 1
    print("Auto-test concluant.")
    return 0


if __name__ == "__main__":
    if "--auto-test" in sys.argv:
        raise SystemExit(auto_test())
    print("Auto-test des scripts de vérification ADR (#2467) :")
    for essai in (
        test_0008_echec_silencieux,
        test_3053_capture_libelle,
        test_0010_dialogue_hors_port,
        test_0035_pictogramme,
        test_0037_slot_actions,
        test_2843_tiret_cadratin,
        test_2843_prose_documentation,
        test_2843_zone_vide_est_une_erreur,
        test_2843_couverture_distingue_dedans_dehors,
        test_2843_balayage_non_recursif,
        test_2493_modale_suit_croissance,
        test_2635_refus_sans_surface,
        test_3947_message_enveloppe,
        test_4359_javadoc_narratif,
        test_4366_avertissement_en_pictogramme,
        test_4366_pictogramme_en_tete_de_ligne,
        test_4368_apostrophe_en_libelle,
        test_4783_traces_d_outil,
        test_4395_renvois_en_javadoc,
        test_4359_blocs_relus,
        test_loupe_4359_javadoc_vieillie,
        test_loupe_0020,
        test_loupe_0044,
        test_4472_commentaire_en_corps,
        test_4468_javadoc_non_relue,
        test_5068_clic_sur_reference_tenue,
        test_4974_attente_reinventee,
        test_4475_stage_non_dimensionne,
        test_4617_code_mort_et_zone_de_test,
        test_4476_javadoc_raconte_son_extraction,
        test_4477_longueur_des_adr,
        test_loupe_4472_densite_de_commentaire,
        test_un_verdict_se_rend_sur_le_numero_de_son_adr,
        test_les_gardes_de_code_lisent_les_deux_arbres,
        test_un_plancher_perime_refuse,
        test_une_population_vide_refuse,
        test_une_loupe_muette_le_dit_sans_bloquer,
        test_un_plancher_sans_population_dit_pourquoi,
        test_le_rapport_lit_encore_les_trois_lignes,
        test_le_contrat_a_une_forme_et_refuse_l_incomplet,
        test_resserre_cliquets_appelle_le_rapport,
        test_rapport_et_resserrement,
        test_le_refus_s_eprouve_sur_un_garde_reel,
    ):
        essai()
    decouverts = _completude()
    if decouverts:
        print(
            f"\n{len(decouverts)} détecteur(s) sans cas témoin : " + ", ".join(decouverts),
            file=sys.stderr,
        )
        print(
            "   Ils ne sont tenus que par leur cliquet, c'est-à-dire par un compte qui ne monte pas.\n"
            "   C'est le trou que ce harnais existe pour fermer.",
            file=sys.stderr,
        )
    if _echecs:
        print(
            f"\n{len(_echecs)} cas en échec : un script ne détecte plus ce qu'il devrait.",
            file=sys.stderr,
        )
    if _echecs or decouverts:
        sys.exit(1)
    print(
        f"\nLes {len(_charges)} scripts chargés détectent leur violation témoin et ignorent les "
        "commentaires ;\naucun détecteur lancé par « Cliquets ADR » n'est sans cas."
    )
