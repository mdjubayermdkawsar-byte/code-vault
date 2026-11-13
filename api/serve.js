module.exports = async (req, res) => {
  const { project, file } = req.query;

  if (!project || !file) {
    return res.status(400).send("Bad Request: Missing project or file parameter");
  }

  const GITHUB_USERNAME = "mdjubayermdkawsar-byte";
  const GITHUB_REPO = "code-vault";

  try {
    const filePath = `vault/${project}/${file}`;
    const url = `https://raw.githubusercontent.com/${GITHUB_USERNAME}/${GITHUB_REPO}/main/${filePath}`;

    const ghRes = await fetch(url);
    if (!ghRes.ok) {
      return res.status(404).send("File Not Found");
    }

    const arrayBuffer = await ghRes.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);

    res.setHeader('Content-Type', 'application/octet-stream');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate');
    return res.status(200).send(buffer);

  } catch (error) {
    return res.status(500).send("Server Error: " + error.message);
  }
};
