#!/bin/bash
# ============================================================
# DS-FTP Testbed Script
# CP372 - Assignment 2
# ============================================================
# Usage: bash testbed.sh
# Run from the PARENT folder containing Sender/ and Receiver/
# ============================================================

RESULTS_FILE="results.txt"
RCV_DATA_PORT=5000
SENDER_ACK_PORT=5001
TIMEOUT=1000
SMALL_FILE="small_test.bin"
LARGE_FILE="large_test.bin"
OUTPUT_FILE="/tmp/ds_ftp_output.bin"
SENDER_LOG="/tmp/sender_log.txt"
RECEIVER_LOG="/tmp/receiver_log.txt"

# ============================================================
# Generate test files
# ============================================================
echo "Generating test files..."
dd if=/dev/urandom of=$SMALL_FILE bs=1024 count=2  2>/dev/null
dd if=/dev/urandom of=$LARGE_FILE bs=1024 count=512 2>/dev/null
echo "Small: $(du -h $SMALL_FILE | cut -f1)   Large: $(du -h $LARGE_FILE | cut -f1)"
echo ""

# ============================================================
# Compile
# ============================================================
echo "Compiling..."
(cd Sender   && javac *.java) || { echo "Sender compile FAILED"; exit 1; }
(cd Receiver && javac *.java) || { echo "Receiver compile FAILED"; exit 1; }
echo "Compile OK"
echo ""

# ============================================================
# run_once FILE WINDOW RN
# Runs receiver + sender once, prints the time in seconds
# ============================================================
run_once() {
    local FILE=$1
    local WINDOW=$2
    local RN=$3
    local ABS_FILE
    ABS_FILE=$(realpath "$FILE")

    # Start receiver in background
    (cd Receiver && java Receiver 127.0.0.1 $SENDER_ACK_PORT $RCV_DATA_PORT \
        "$OUTPUT_FILE" $RN > "$RECEIVER_LOG" 2>&1) &
    local RCV_PID=$!

    sleep 0.5  # give receiver time to bind

    # Run sender and save output to log
    if [ "$WINDOW" -eq 0 ]; then
        (cd Sender && java Sender 127.0.0.1 $RCV_DATA_PORT $SENDER_ACK_PORT \
            "$ABS_FILE" $TIMEOUT > "$SENDER_LOG" 2>&1)
    else
        (cd Sender && java Sender 127.0.0.1 $RCV_DATA_PORT $SENDER_ACK_PORT \
            "$ABS_FILE" $TIMEOUT $WINDOW > "$SENDER_LOG" 2>&1)
    fi

    wait $RCV_PID

    # Extract time from sender log
    # Looks for: "Total Transmission Time: X.XX seconds"
    local TIME
    TIME=$(grep "Total Transmission Time" "$SENDER_LOG" | grep -oE '[0-9]+\.[0-9]+')

    if [ -z "$TIME" ]; then
        echo "--- Sender output for failed run ---" >&2
        cat "$SENDER_LOG" >&2
        TIME="ERR"
    fi

    # Check file integrity
    local INTEGRITY
    if diff -q "$ABS_FILE" "$OUTPUT_FILE" > /dev/null 2>&1; then
        INTEGRITY="PASS"
    else
        INTEGRITY="FAIL"
    fi

    echo "$TIME $INTEGRITY"
}

# ============================================================
# average_runs FILE WINDOW RN
# Runs 3 times, prints each result, computes average
# ============================================================
average_runs() {
    local FILE=$1
    local WINDOW=$2
    local RN=$3
    local TOTAL=0
    local ALL_PASS=true

    for i in 1 2 3; do
        RESULT=$(run_once "$FILE" "$WINDOW" "$RN")
        T=$(echo "$RESULT" | awk '{print $1}')
        I=$(echo "$RESULT" | awk '{print $2}')

        if [ "$T" == "ERR" ]; then
            ALL_PASS=false
            echo "  Run $i: FAILED [$I]"
        else
            TOTAL=$(echo "$TOTAL + $T" | bc)
            echo "  Run $i: ${T}s [$I]"
        fi

        [ "$I" != "PASS" ] && ALL_PASS=false
    done

    local AVG
    AVG=$(echo "scale=2; $TOTAL / 3" | bc)
    $ALL_PASS && INTEG_LABEL="PASS" || INTEG_LABEL="FAIL"

    echo "  --> Average: ${AVG}s | Integrity: $INTEG_LABEL"
}

# ============================================================
# Run all combinations
# ============================================================
> "$RESULTS_FILE"

echo "============================================================" | tee -a "$RESULTS_FILE"
echo " DS-FTP Performance Results (Avg of 3 runs)"                 | tee -a "$RESULTS_FILE"
echo "============================================================" | tee -a "$RESULTS_FILE"

WINDOWS=(0 20 40 80)
RNS=(0 5 100)
FILES=("$SMALL_FILE" "$LARGE_FILE")

for FILE in "${FILES[@]}"; do

    [ "$FILE" == "$SMALL_FILE" ] && LABEL="Small (<4KB)" || LABEL="Large (~512KB)"

    echo "" | tee -a "$RESULTS_FILE"
    echo "------------------------------------------------------------" | tee -a "$RESULTS_FILE"
    echo " File: $LABEL"                                                | tee -a "$RESULTS_FILE"
    echo "------------------------------------------------------------" | tee -a "$RESULTS_FILE"

    for WINDOW in "${WINDOWS[@]}"; do

        [ "$WINDOW" -eq 0 ] && PROTO="Stop-and-Wait" || PROTO="GBN window=$WINDOW"

        for RN in "${RNS[@]}"; do

            echo "" | tee -a "$RESULTS_FILE"
            echo " Protocol: $PROTO | RN=$RN" | tee -a "$RESULTS_FILE"
            average_runs "$FILE" "$WINDOW" "$RN" | tee -a "$RESULTS_FILE"

        done
    done
done

echo "" | tee -a "$RESULTS_FILE"
echo "============================================================" | tee -a "$RESULTS_FILE"
echo " Done! Full results saved to: $RESULTS_FILE"
echo "============================================================"