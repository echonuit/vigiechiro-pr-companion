"""Redessine, sur le site, le bandeau que la conversion OKF a fait passer en en-tete (chantier A).

Avant la conversion, le statut, le chantier et la verification d une ADR etaient trois PUCES, donc
visibles. Passees en frontmatter, elles deviennent lisibles par la machine et invisibles pour
l humain : MkDocs les retire du corps rendu. Ce serait un recul, c est a dire l inverse de ce que le
chantier cherche. Ce hook les remet sous le titre, depuis la meme et unique source.

**Il emet du HTML, pas du markdown, et ce n est pas un gout.** Le spike a monte la mutation : en
emettant une admonition `!!! info`, le bandeau depend de l extension `admonition`. Retiree, elle
laisse la syntaxe brute visible dans la page. Un bandeau qui se degrade en texte apparent selon la
configuration du site n est pas un bandeau, c est une panne discrete.

**Il n emet aucun cadratin, pas meme en entite.** Un `&mdash;` traverserait le cliquet du tiret
cadratin, qui cherche le glyphe, et afficherait pourtant le glyphe au lecteur. Un garde qu on
contourne par l encodage ne garde plus rien.

**L enonce de l article vient de `CONSTITUTION.md`**, lu une fois a la construction. Un code seul
(`A18`) ne dit rien a qui ouvre une ADR, et recopier l enonce dans chaque en-tete le ferait deriver.
Un article cite par une ADR et absent de la constitution ARRETE la construction : c est un
rattachement casse, et le site ne doit pas le rendre en silence.
"""

import html
import pathlib
import re

NIVEAUX = {
    "certaine": ("Vérification certaine", "un test ou un script déterministe la tient"),
    "probable": ("Vérification probable", "un script de suspects et son cliquet la tiennent"),
    "humaine": ("Vérification humaine", "non mécanisée, et le motif est déclaré"),
}

ARTICLE = re.compile(r"^###\s+(A\d+)\s*:\s*(.+?)\s*$", re.M)

_articles: dict[str, str] = {}


def lit_articles(chemin: pathlib.Path) -> dict[str, str]:
    """Les articles de la constitution, code vers enonce."""
    return dict(ARTICLE.findall(chemin.read_text(encoding="utf-8")))


def on_config(config):
    global _articles
    racine = pathlib.Path(config["config_file_path"]).resolve().parent
    _articles = lit_articles(racine / "CONSTITUTION.md")
    return config


def _code(valeur: str) -> str:
    """Echappe, puis rend les chevrons de code en `<code>`.

    Le champ `chantier` cite souvent un fichier ou un fragment entre chevrons. Echappe sans plus, le
    lecteur voit les chevrons eux-memes : le bandeau afficherait sa propre syntaxe.
    """
    morceaux = html.escape(valeur).split("`")
    return "".join(
        m if i % 2 == 0 else f"<code>{m}</code>" for i, m in enumerate(morceaux)
    ) if len(morceaux) % 2 else html.escape(valeur)


def _ligne(intitule: str, valeur: str) -> str:
    return (
        '<div class="adr-bandeau__ligne">'
        f'<span class="adr-bandeau__cle">{html.escape(intitule)}</span>'
        f'<span class="adr-bandeau__valeur">{valeur}</span>'
        "</div>"
    )


def bandeau(meta: dict, articles: dict[str, str] | None = None) -> str:
    """Le bandeau HTML d une ADR, depuis ses seules metadonnees."""
    articles = _articles if articles is None else articles
    lignes = []

    statut = str(meta.get("status", "")).strip()
    date = str(meta.get("decided_at", "")).strip()
    if statut:
        libelle = "En vigueur" if statut == "stable" else "Dépassée"
        if date:
            libelle += f", {date}"
        lignes.append(_ligne("Statut", html.escape(libelle)))

    code = str(meta.get("article", "")).strip()
    if code:
        if code not in articles:
            raise ValueError(
                f"l'ADR « {meta.get('title', '?')} » se rattache à l'article {code}, "
                "que CONSTITUTION.md ne déclare pas"
            )
        lignes.append(_ligne("Article", f"{html.escape(code)} · {html.escape(articles[code])}"))

    chantier = str(meta.get("chantier", "")).strip()
    if chantier:
        lignes.append(_ligne("Chantier", _code(chantier)))

    niveau = str(meta.get("verification", "")).strip()
    if niveau:
        titre, glose = NIVEAUX.get(niveau, (f"Vérification {niveau}", ""))
        tenu = meta.get("enforced_by") or []
        if tenu:
            glose = ", ".join(f"<code>{html.escape(str(t))}</code>" for t in tenu)
        elif meta.get("verification_note"):
            glose = html.escape(str(meta["verification_note"]))
        lignes.append(_ligne(titre, glose))

    if not lignes:
        return ""
    return '<div class="adr-bandeau">' + "".join(lignes) + "</div>"


def on_page_markdown(markdown, page, config, files):  # noqa: ARG001  (signature MkDocs)
    if str(page.meta.get("type", "")) != "adr":
        return markdown
    rendu = bandeau(page.meta)
    if not rendu:
        return markdown
    # Le bandeau se pose APRES le titre de niveau 1 : une page qui commencerait par un bloc HTML
    # perdrait son titre dans le sommaire du theme.
    lignes = markdown.splitlines()
    for i, ligne in enumerate(lignes):
        if ligne.startswith("# "):
            return "\n".join(lignes[: i + 1] + ["", rendu, ""] + lignes[i + 1 :])
    return rendu + "\n\n" + markdown
