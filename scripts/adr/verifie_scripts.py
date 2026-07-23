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
import sys
import tempfile

ICI = pathlib.Path(__file__).parent
_echecs: list[str] = []


def _charge(nom: str):
    """Importe un script au nom non-importable (chiffres, tirets) par son chemin."""
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
            "Exemple.java",
            "class E {\n"
            "  void a() { try { x(); } catch (Exception e) { } }\n"  # vide -> compte
            "  void b() { try { x(); } catch (Exception e) { log(e); } }\n"  # tracé -> non
            "  void c() { try { x(); } catch (Exception e) { /* ignoré */ } }\n"  # commentaire seul -> compte
            "  /* void mort() { try{}catch(E e){} } */\n"  # catch EN commentaire -> non
            "}\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0008 détecte les catch vides (vide + commentaire-seul)", n, 2)


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
            '<VBox>\n'
            '  <Button text="\U0001f5d1 Supprimer"/>\n'  # pictogramme dans un libellé -> compte
            '  <!-- \U0001f5d1 en commentaire, prose autorisée -->\n'  # commentaire -> non
            "</VBox>\n",
        )
        n = len(m.suspects(sources=racine))
        _verifie("0035 détecte un pictogramme, ignore le commentaire", n, 1)


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


def test_rapport_et_resserrement() -> None:
    rapport = _charge("rapport.py")
    # Le parsing : une ligne normalisée doit être reconnue.
    ligne = "ADR 0099 | suspects=2 | cliquet=5 | verdict=a-resserrer"
    trouve = rapport.LIGNE_CLIQUET.search(ligne)
    _verifie("rapport.py parse une ligne de cliquet", bool(trouve), True)
    # La détection de resserrement : cliquet 5 pour 2 suspects -> ramener à 2.
    props = rapport.resserrements([("0099", 2, 5, "a-resserrer")])
    _verifie("rapport.py propose de resserrer 5 -> 2", props, [("0099", 2)])
    # Aucune proposition quand la marge colle.
    props2 = rapport.resserrements([("0099", 5, 5, "ok")])
    _verifie("rapport.py ne resserre pas une marge exacte", props2, [])


if __name__ == "__main__":
    print("Auto-test des scripts de vérification ADR (#2467) :")
    for essai in (
        test_0008_echec_silencieux,
        test_0010_dialogue_hors_port,
        test_0035_pictogramme,
        test_0037_slot_actions,
        test_loupe_0020,
        test_rapport_et_resserrement,
    ):
        essai()
    if _echecs:
        print(f"\n{len(_echecs)} cas en échec : un script ne détecte plus ce qu'il devrait.", file=sys.stderr)
        sys.exit(1)
    print("\nTous les scripts détectent leur violation témoin et ignorent les commentaires.")
