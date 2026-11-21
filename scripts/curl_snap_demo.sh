#!/bin/bash
# Demo endpoint SNAP /v1.0/loan-funding. Jalankan aplikasi dulu:
#   java -jar generated/target/p2p-lending-migrated-0.1.0.jar --spring.profiles.active=postgres --server.port=8089
# Data awal: loan L001 (target 10 juta, PENDING), lender LD001 (saldo 20 juta) di p2p_baru.
U="${1:-http://localhost:8089}/v1.0/loan-funding"

post() {   # post <external-id> <body> [tanpa-signature]
  if [ "$3" = "tanpa-signature" ]; then
    curl -s --retry 40 --retry-connrefused --retry-delay 2 -X POST "$U" \
      -H 'Content-Type: application/json' -H 'Authorization: Bearer tokenuji' \
      -H 'X-TIMESTAMP: 2026-11-21T10:15:00+07:00' -H 'X-PARTNER-ID: LENDERAPP01' \
      -H "X-EXTERNAL-ID: $1" -H 'CHANNEL-ID: 95221' -d "$2" -w '  [HTTP %{http_code}]\n'
  else
    curl -s --retry 40 --retry-connrefused --retry-delay 2 -X POST "$U" \
      -H 'Content-Type: application/json' -H 'Authorization: Bearer tokenuji' \
      -H 'X-TIMESTAMP: 2026-11-21T10:15:00+07:00' -H 'X-SIGNATURE: YWJjZA==' -H 'X-PARTNER-ID: LENDERAPP01' \
      -H "X-EXTERNAL-ID: $1" -H 'CHANNEL-ID: 95221' -d "$2" -w '  [HTTP %{http_code}]\n'
  fi
}

echo "1) sukses:"
post 1001 '{"partnerReferenceNo":"REF-1","fundingId":"F001","loanId":"L001","lenderId":"LD001","jumlah":{"value":"4000000.00","currency":"IDR"}}'
echo "2) loan tidak ada:"
post 1002 '{"partnerReferenceNo":"REF-2","fundingId":"F002","loanId":"L999","lenderId":"LD001","jumlah":{"value":"1.00","currency":"IDR"}}'
echo "3) X-EXTERNAL-ID ganda (1001 lagi):"
post 1001 '{"partnerReferenceNo":"REF-3","fundingId":"F003","loanId":"L001","lenderId":"LD001","jumlah":{"value":"1.00","currency":"IDR"}}'
echo "4) tanpa X-SIGNATURE:"
post 1004 '{}' tanpa-signature
echo "5) nominal salah format:"
post 1005 '{"partnerReferenceNo":"REF-5","fundingId":"F005","loanId":"L001","lenderId":"LD001","jumlah":{"value":"100","currency":"IDR"}}'
echo "6) melebihi sisa target (pesan asli RAISE tidak bocor ke klien):"
post 1006 '{"partnerReferenceNo":"REF-6","fundingId":"F006","loanId":"L001","lenderId":"LD001","jumlah":{"value":"7000000.00","currency":"IDR"}}'
