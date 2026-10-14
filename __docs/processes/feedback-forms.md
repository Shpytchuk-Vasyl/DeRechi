# Feedback forms (Tally)

Site feedback goes to [Tally](https://tally.so), one form per language. The feedback icon next to the
language switcher opens the form of the visitor's language as a popup, and the footer links to it
(see [Web-Client → Feedback](../architecture/modules/web-client.md#feedback)). This page is how to
build the five forms so they behave the same and look like the site.

Build the Ukrainian form completely first, test it, then duplicate it four times and translate.
Duplicating keeps the theme, the logic and the hidden fields, so only texts change.

## 1. The form

Create a form (**Create form**, blank) and add the blocks in this order. A block is added with `/`
and its name.

| # | Block | Text (Ukrainian; all languages in [section 4](#4-texts-in-five-languages)) | Settings |
|---|---|---|---|
| 1 | Form title | Відгук про DeRechi | |
| 2 | Text | Розкажіть, що покращити або що не працює. Ми читаємо кожен відгук. | |
| 3 | `/multiple` (Multiple choice) | Про що ваш відгук? Options: Ідея або пропозиція · Щось не працює · Питання · Інше | **Required** |
| 4 | `/logic` | When block 3 **is** «Щось не працює» → Then **Show blocks** → block 7 | |
| 5 | `/logic` | When block 3 **is** «Ідея або пропозиція» → Then **Show blocks** → block 8 | |
| 6 | `/long` (Long answer) | Розкажіть детальніше | **Required**; placeholder and help text from section 4 |
| 7 | `/file` (File upload) | Скриншот, якщо є | Optional; **hidden** (⋮⋮ → Hide). Free, up to 10 MB a file |
| 8 | `/rating` | Як вам DeRechi загалом? | Optional, 5 stars; **hidden** (⋮⋮ → Hide) |
| 9 | `/email` | Пошта для відповіді | Optional; help text from section 4 |
| 10 | `/hidden` (Hidden fields) | `page` | Exactly this name, lowercase |

Then the button: click the submit button in the editor and change its text to «Надіслати».

Why the logic: the rating makes sense only for an idea. Someone reporting a bug is annoyed at that
moment and rates the bug, not the site, so the rating is not shown to them; a screenshot is the
useful extra there instead. Tally hides a block that a **Show blocks** action targets only if the
block is hidden by hand first (⋮⋮ → **Hide**), which is why blocks 7 and 8 are hidden.

Check the logic in **Preview**: choosing «Щось не працює» must reveal the screenshot, «Ідея або
пропозиція» the rating, the other two neither. If the blocks do not appear on the same page, put
block 3 and the two logic blocks on their own first page (`/page` after block 5), so the logic
runs when the visitor presses Next.

### Hidden fields

The site fills one field, `page`: the page the icon was pressed on, without the language prefix
(`/lost/42`); from the footer it is `footer`. The language needs no field, as every language has its
own form.

Do not add a field named `originPage` or a field named after a URL parameter of the site. Tally's
popup forwards the current page's path as `originPage` and every query parameter of the page to
the form, and stores whatever matches a hidden field. The site overwrites `originPage` with the
same safe value as `page`, as the path of a claim page carries the claim token, but there is no
reason to store any of it.

## 2. Settings

On the form's **Settings** tab:

- **Language**: the language of this form (Українська, English, Polski, Deutsch, Français). It
  translates the texts we cannot edit: error messages and the closed-form page.
- **Email notifications → Self email notifications**: on, to the address that should get the
  feedback (free). Every submission arrives as an email with the answers. Tally does not promise
  hidden fields in the free email (putting them into the email is Pro), so if `page` is not there,
  it is in the form's **Submissions** tab.
- **Thank you page**: type `/thank` at the end of the form and write the short text from section 4.
  The popup closes by itself three seconds after the form is sent, so keep it to one line.

Nothing in Tally's own popup settings ("Get the code") is needed: the site opens the popup itself
(`Tally.openPopup`, as wide as the screen up to 600 px, closes 3 s after submitting). Do not paste Tally's embed code into
the site.

If spam starts, add `/recaptcha` right above the submit button (free, reCAPTCHA v2, a challenge only
for suspicious visitors). Not needed from the start.

## 3. Look like the site

**Customize** (above the form title) → theme **Custom**. Everything below is free.

| Setting | Value | Site token |
|---|---|---|
| Form background | `#F0E9FF` | `--color-bg` |
| Text | `#3A2E5C` | `--color-ink` |
| Button | `#C9A8FF` | `--color-purple`, the main buttons |
| Accents (links, selected options, focused inputs) | `#C9A8FF` | `--color-purple` |
| Font | Nunito | the site's font |

- In **Preview**, check the button text. The site writes dark text (`#3A2E5C`) on the purple
  button; Tally picks the text color itself. If it comes out white and hard to read, set the
  button to `#3A2E5C` instead.
- **Logo**: `Web-Client/src/app/apple-icon.png` (180 × 180, the site's icon). Tally recommends
  200 × 200, 180 is fine.
- **Cover**: none. The popup is small, a cover pushes the questions below the fold.

What stays different, as it needs Tally Pro: round corners of inputs and buttons (the site uses
20 px), custom CSS, the "Made with Tally" badge, the link preview.

## 4. Texts in five languages

No long dashes in any of them, like everywhere on the site.

| Element | uk | en | pl | de | fr |
|---|---|---|---|---|---|
| Form title | Відгук про DeRechi | Feedback on DeRechi | Opinia o DeRechi | Feedback zu DeRechi | Votre avis sur DeRechi |
| Intro text | Розкажіть, що покращити або що не працює. Ми читаємо кожен відгук. | Tell us what to improve or what is not working. We read every message. | Napisz, co poprawić albo co nie działa. Czytamy każdą wiadomość. | Sagen Sie uns, was wir verbessern können oder was nicht funktioniert. Wir lesen jede Nachricht. | Dites-nous ce qu'il faut améliorer ou ce qui ne marche pas. Nous lisons chaque message. |
| Question 3 | Про що ваш відгук? | What is it about? | Czego dotyczy Twoja opinia? | Worum geht es? | De quoi s'agit-il ? |
| Option: idea | Ідея або пропозиція | An idea or suggestion | Pomysł lub sugestia | Eine Idee oder ein Vorschlag | Une idée ou une suggestion |
| Option: bug | Щось не працює | Something is not working | Coś nie działa | Etwas funktioniert nicht | Quelque chose ne marche pas |
| Option: question | Питання | A question | Pytanie | Eine Frage | Une question |
| Option: other | Інше | Something else | Coś innego | Etwas anderes | Autre chose |
| Question 6 | Розкажіть детальніше | Tell us more | Opisz to dokładniej | Erzählen Sie uns mehr | Dites-nous en plus |
| Placeholder 6 | Що сталося або що хочете змінити? | What happened, or what would you change? | Co się stało albo co chcesz zmienić? | Was ist passiert oder was würden Sie ändern? | Que s'est-il passé, ou que changeriez-vous ? |
| Help text 6 | Не пишіть тут номери телефонів і дані документів. | Please don't include phone numbers or document details. | Nie podawaj tu numerów telefonów ani danych z dokumentów. | Bitte keine Telefonnummern oder Ausweisdaten angeben. | N'indiquez pas de numéros de téléphone ni de données de documents. |
| Question 7 | Скриншот, якщо є | A screenshot, if you have one | Zrzut ekranu, jeśli masz | Ein Screenshot, falls vorhanden | Une capture d'écran, si vous en avez une |
| Question 8 | Як вам DeRechi загалом? | How do you like DeRechi overall? | Jak ogólnie oceniasz DeRechi? | Wie gefällt Ihnen DeRechi insgesamt? | Que pensez-vous de DeRechi dans l'ensemble ? |
| Question 9 | Пошта для відповіді | Email for a reply | E-mail do odpowiedzi | E-Mail für eine Antwort | E-mail pour vous répondre |
| Help text 9 | Необовʼязково. Залиште, якщо хочете, щоб ми відповіли. | Optional. Leave it if you'd like a reply. | Opcjonalnie. Zostaw, jeśli chcesz odpowiedzi. | Optional. Hinterlassen Sie sie, wenn Sie eine Antwort möchten. | Facultatif. Laissez-le si vous souhaitez une réponse. |
| Submit button | Надіслати | Send | Wyślij | Senden | Envoyer |
| Thank you page | Дякуємо! Відгук надіслано. | Thank you! Your feedback is sent. | Dziękujemy! Opinia wysłana. | Danke! Ihr Feedback ist angekommen. | Merci ! Votre avis a bien été envoyé. |

French puts a space before `?` and `!`, as written above.

## 5. Four more languages

For each of en, pl, de, fr:

1. On the Ukrainian form: **⋯ → Duplicate**.
2. Replace every text with the column of that language. Rename the options of question 3 in place
   rather than deleting and re-adding them, so the two logic blocks keep pointing at them; open
   both logic blocks afterwards and check that they still name the bug and the idea option.
3. **Settings → Language** to that language.
4. **Preview**: the bug option shows the screenshot, the idea option shows the rating.
5. **Publish**.

## 6. Connect to the site

Each published form has a link `https://tally.so/r/<id>`; the `<id>` is what the site needs.

```
NEXT_PUBLIC_TALLY_FORM=uk=<id>,en=<id>,pl=<id>,de=<id>,fr=<id>
```

- Set it in Vercel (Production) and in `Web-Client/.env.local`. It is a `NEXT_PUBLIC_` variable,
  inlined at build time: the site needs a rebuild and redeploy, not a restart.
- A language without its form falls back to the Ukrainian one, then to the first form in the list; an empty
  variable hides the icon and the footer link.
- The e2e test of the feedback popup needs the variable in `.env.local` (any ids work, see
  `e2e/README.md`).

Check on the deployed site: switch to each language, press the icon, the popup must be in that
language; send one test answer per form and check that the email has `page`.
