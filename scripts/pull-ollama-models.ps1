# Pull Dosya's default Ollama models into the dossia-ollama container.
# Usage (from repo root, after: docker compose up -d ollama):
#   .\scripts\pull-ollama-models.ps1

$ErrorActionPreference = "Stop"
$ChatModel = if ($env:OLLAMA_CHAT_MODEL) { $env:OLLAMA_CHAT_MODEL } else { "qwen2.5:7b-instruct" }
$EmbedModel = if ($env:OLLAMA_EMBEDDING_MODEL) { $env:OLLAMA_EMBEDDING_MODEL } else { "nomic-embed-text" }

Write-Host "Waiting for dossia-ollama..."
$ready = $false
for ($i = 0; $i -lt 30; $i++) {
  docker exec dossia-ollama ollama list 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) { $ready = $true; break }
  Start-Sleep -Seconds 2
}
if (-not $ready) {
  throw "Ollama container not ready. Run: docker compose up -d ollama"
}

Write-Host "Pulling chat model: $ChatModel"
docker exec dossia-ollama ollama pull $ChatModel
Write-Host "Pulling embedding model: $EmbedModel"
docker exec dossia-ollama ollama pull $EmbedModel
Write-Host "Done. Point Spring at http://localhost:11434 (OLLAMA_BASE_URL) and set LLM_PROVIDER=ollama or auto."
