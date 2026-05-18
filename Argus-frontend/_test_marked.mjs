import { marked, Renderer } from 'marked'

const r = new Renderer()
r.link = ({ href, text }) => `<a href="${href}" target="_blank" rel="noopener">${text}</a>`
marked.use({ renderer: r })

const md = `# Hello World

This is **bold** and *italic* and ~~strikethrough~~.

- Item 1
- Item 2

\`\`\`
const x = 1;
\`\`\`

> Blockquote

| Col A | Col B |
|-------|-------|
| 1     | 2     |

[Google](https://google.com)
`

const html = await marked.parse(md)
console.log(html)
