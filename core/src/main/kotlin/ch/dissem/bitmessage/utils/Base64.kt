/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.dissem.bitmessage.utils


/**
 * Utilities for encoding and decoding the Base64 representation of
 * binary data.  See RFCs <a
 * href="http://www.ietf.org/rfc/rfc2045.txt">2045</a> and <a
 * href="http://www.ietf.org/rfc/rfc3548.txt">3548</a>.
 */
object Base64 {
    /**
     * Encoder flag bit to omit the padding '=' characters at the end
     * of the output (if any).
     */
    const val NO_PADDING = 1

    /**
     * Encoder flag bit to omit all line terminators (i.e., the output
     * will be on one long line).
     */
    const val NO_WRAP = 2

    /**
     * Encoder flag bit to indicate lines should be terminated with a
     * CRLF pair instead of just an LF.  Has no effect if `NO_WRAP` is specified as well.
     */
    const val CRLF = 4

    /**
     * Encoder/decoder flag bit to indicate using the "URL and
     * filename safe" variant of Base64 (see RFC 3548 section 4) where
     * `-` and `_` are used in place of `+` and
     * `/`.
     */
    const val URL_SAFE = 8

    /**
     * Default values for encoder/decoder flags.
     */
    const val DEFAULT = NO_WRAP


    //  --------------------------------------------------------
    //  decoding
    //  --------------------------------------------------------

    /**
     * Decode the Base64-encoded data in input and return the data in
     * a new byte array.

     *
     * The padding '=' characters at the end are considered optional, but
     * if any are present, there must be the correct number of them.

     * @param str    the input String to decode, which is converted to
     * *               bytes using the default charset
     * *
     * @param flags  controls certain features of the decoded output.
     * *               Pass `DEFAULT` to decode standard Base64.
     * *
     * *
     * @throws IllegalArgumentException if the input contains
     * * incorrect padding
     */
    @JvmStatic
    fun decode(str: String, flags: Int = DEFAULT): ByteArray {
        val input = str.toByteArray(Charsets.US_ASCII)
        return decode(input, 0, input.size, flags)
    }

    /**
     * Decode the Base64-encoded data in input and return the data in
     * a new byte array.

     *
     * The padding '=' characters at the end are considered optional, but
     * if any are present, there must be the correct number of them.

     * @param input  the data to decode
     * *
     * @param offset the position within the input array at which to start
     * *
     * @param len    the number of bytes of input to decode
     * *
     * @param flags  controls certain features of the decoded output.
     * *               Pass `DEFAULT` to decode standard Base64.
     * *
     * *
     * @throws IllegalArgumentException if the input contains
     * * incorrect padding
     */
    @JvmStatic
    fun decode(input: ByteArray, offset: Int, len: Int, flags: Int = DEFAULT): ByteArray {
        // Allocate space for the most data the input could represent.
        // (It could contain less if it contains whitespace, etc.)
        val decoder = Decoder(Options(flags), ByteArray(len * 3 / 4))

        if (!decoder.process(input, offset, len, true)) {
            throw IllegalArgumentException("bad base-64")
        }

        // Maybe we got lucky and allocated exactly enough output space.
        if (decoder.op == decoder.output.size) {
            return decoder.output
        }

        // Need to shorten the array, so allocate a new one of the
        // right size and copy.
        val temp = ByteArray(decoder.op)
        System.arraycopy(decoder.output, 0, temp, 0, decoder.op)
        return temp
    }

    //  --------------------------------------------------------
    //  encoding
    //  --------------------------------------------------------

    /**
     * Base64-encode the given data and return a newly allocated
     * String with the result.

     * @param input  the data to encode
     * *
     * @param flags  controls certain features of the encoded output.
     * *               Passing `DEFAULT` results in output that
     * *               adheres to RFC 2045.
     */
    fun encodeToString(input: ByteArray, flags: Int = DEFAULT): String {
        return String(encode(input, 0, input.size, flags), Charsets.US_ASCII)
    }

    /**
     * Base64-encode the given data and return a newly allocated
     * byte[] with the result.

     * @param input  the data to encode
     * *
     * @param offset the position within the input array at which to
     * *               start
     * *
     * @param len    the number of bytes of input to encode
     * *
     * @param flags  controls certain features of the encoded output.
     * *               Passing `DEFAULT` results in output that
     * *               adheres to RFC 2045.
     */
    fun encode(input: ByteArray, offset: Int, len: Int, flags: Int): ByteArray {
        // Compute the exact length of the array we will produce.
        var output_len = len / 3 * 4

        val options = Options(flags)

        // Account for the tail of the data and the padding bytes, if any.
        if (options.do_padding) {
            if (len % 3 > 0) {
                output_len += 4
            }
        } else {
            when (len % 3) {
                0 -> {
                }
                1 -> output_len += 2
                2 -> output_len += 3
            }
        }

        // Account for the newlines, if any.
        if (options.do_newline && len > 0) {
            output_len += ((len - 1) / (3 * Encoder.LINE_GROUPS) + 1) * if (options.do_cr) 2 else 1
        }

        val encoder = Encoder(options, ByteArray(output_len))
        encoder.process(input, offset, len, true)

        assert(encoder.op == output_len)

        return encoder.output
    }
}

class Options(flags: Int) {
    val do_padding = flags and Base64.NO_PADDING == 0
    val do_newline = flags and Base64.NO_WRAP == 0
    val do_cr = flags and Base64.CRLF != 0
    val url_safe = flags and Base64.URL_SAFE == 0
}

private abstract class Coder(val output: ByteArray) {
    var op = 0

    /**
     * Encode/decode another block of input data.  this.output is
     * provided by the caller, and must be big enough to hold all
     * the coded data.  On exit, this.opwill be set to the length
     * of the coded data.
     *
     * @param finish true if this is the final call to process for
     *        this object.  Will finalize the coder state and
     *        include any final bytes in the output.
     *
     * @return true if the input so far is good; false if some
     *         error has been detected in the input stream..
     */
    abstract fun process(input: ByteArray, offset: Int, len: Int, finish: Boolean): Boolean

    /**
     * @return the maximum number of bytes a call to process()
     *         could produce for the given number of input bytes.  This may
     *         be an overestimate.
     */
    abstract fun maxOutputSize(len: Int): Int
}

private class Decoder(options: Options, output: ByteArray) : Coder(output) {

    /**
     * States 0-3 are reading through the next input tuple.
     * State 4 is having read one '=' and expecting exactly
     * one more.
     * State 5 is expecting no more data or padding characters
     * in the input.
     * State 6 is the error state; an error has been detected
     * in the input and no future input can "fix" it.
     */
    private var state: Int = 0   // state number (0 to 6)
    private var value: Int = 0

    private val alphabet = if (options.url_safe) DECODE else DECODE_WEBSAFE

    /**
     * @return an overestimate for the number of bytes `len` bytes could decode to.
     */
    override fun maxOutputSize(len: Int): Int {
        return len * 3 / 4 + 10
    }

    /**
     * Decode another block of input data.

     * @return true if the state machine is still healthy.  false if
     * *         bad base-64 data has been detected in the input stream.
     */
    override fun process(input: ByteArray, offset: Int, len: Int, finish: Boolean): Boolean {
        var end = len
        if (this.state == 6) return false

        var p = offset
        end += offset

        // Using local variables makes the decoder about 12%
        // faster than if we manipulate the member variables in
        // the loop.  (Even alphabet makes a measurable
        // difference, which is somewhat surprising to me since
        // the member variable is final.)
        var state = this.state
        var value = this.value
        var op = 0
        val output = this.output
        val alphabet = this.alphabet

        while (p < end) {
            // Try the fast path:  we're starting a new tuple and the
            // next four bytes of the input stream are all data
            // bytes.  This corresponds to going through states
            // 0-1-2-3-0.  We expect to use this method for most of
            // the data.
            //
            // If any of the next four bytes of input are non-data
            // (whitespace, etc.), value will end up negative.  (All
            // the non-data values in decode are small negative
            // numbers, so shifting any of them up and or'ing them
            // together will result in a value with its top bit set.)
            //
            // You can remove this whole block and the output should
            // be the same, just slower.
            if (state == 0) {
                fun nextVal(): Int {
                    value = alphabet[input[p].toInt() and 0xff] shl 18 or
                        (alphabet[input[p + 1].toInt() and 0xff] shl 12) or
                        (alphabet[input[p + 2].toInt() and 0xff] shl 6) or
                        alphabet[input[p + 3].toInt() and 0xff]
                    return value
                }
                while (p + 4 <= end && nextVal() >= 0) {
                    output[op + 2] = value.toByte()
                    output[op + 1] = (value shr 8).toByte()
                    output[op] = (value shr 16).toByte()
                    op += 3
                    p += 4
                }
                if (p >= end) break
            }

            // The fast path isn't available -- either we've read a
            // partial tuple, or the next four input bytes aren't all
            // data, or whatever.  Fall back to the slower state
            // machine implementation.

            val d = alphabet[input[p++].toInt() and 0xff]

            when (state) {
                0 -> if (d >= 0) {
                    value = d
                    ++state
                } else if (d != SKIP) {
                    this.state = 6
                    return false
                }

                1 -> if (d >= 0) {
                    value = value shl 6 or d
                    ++state
                } else if (d != SKIP) {
                    this.state = 6
                    return false
                }

                2 -> if (d >= 0) {
                    value = value shl 6 or d
                    ++state
                } else if (d == EQUALS) {
                    // Emit the last (partial) output tuple;
                    // expect exactly one more padding character.
                    output[op++] = (value shr 4).toByte()
                    state = 4
                } else if (d != SKIP) {
                    this.state = 6
                    return false
                }

                3 -> if (d >= 0) {
                    // Emit the output triple and return to state 0.
                    value = value shl 6 or d
                    output[op + 2] = value.toByte()
                    output[op + 1] = (value shr 8).toByte()
                    output[op] = (value shr 16).toByte()
                    op += 3
                    state = 0
                } else if (d == EQUALS) {
                    // Emit the last (partial) output tuple;
                    // expect no further data or padding characters.
                    output[op + 1] = (value shr 2).toByte()
                    output[op] = (value shr 10).toByte()
                    op += 2
                    state = 5
                } else if (d != SKIP) {
                    this.state = 6
                    return false
                }

                4 -> if (d == EQUALS) {
                    ++state
                } else if (d != SKIP) {
                    this.state = 6
                    return false
                }

                5 -> if (d != SKIP) {
                    this.state = 6
                    return false
                }
            }
        }

        if (!finish) {
            // We're out of input, but a future call could provide
            // more.
            this.state = state
            this.value = value
            this.op = op
            return true
        }

        // Done reading input.  Now figure out where we are left in
        // the state machine and finish up.

        when (state) {
            0 -> {
            }
            1 -> {
                // Read one extra input byte, which isn't enough to
                // make another output byte.  Illegal.
                this.state = 6
                return false
            }
            2 ->
                // Read two extra input bytes, enough to emit 1 more
                // output byte.  Fine.
                output[op++] = (value shr 4).toByte()
            3 -> {
                // Read three extra input bytes, enough to emit 2 more
                // output bytes.  Fine.
                output[op++] = (value shr 10).toByte()
                output[op++] = (value shr 2).toByte()
            }
            4 -> {
                // Read one padding '=' when we expected 2.  Illegal.
                this.state = 6
                return false
            }
            5 -> {
            }
        }// Output length is a multiple of three.  Fine.
        // Read all the padding '='s we expected and no more.
        // Fine.

        this.state = state
        this.op = op
        return true
    }

    companion object {
        /**
         * Lookup table for turning bytes into their position in the
         * Base64 alphabet.
         */
        private val DECODE = intArrayOf(
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63,
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
            -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1,
            -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        )

        /**
         * Decode lookup table for the "web safe" variant (RFC 3548
         * sec. 4) where - and _ replace + and /.
         */
        private val DECODE_WEBSAFE = intArrayOf(
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1,
            52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -2, -1, -1,
            -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
            15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, 63,
            -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
            41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
        )

        /** Non-data values in the DECODE arrays.  */
        private val SKIP = -1
        private val EQUALS = -2
    }
}

private class Encoder(val options: Options, output: ByteArray) : Coder(output) {

    private val alphabet: ByteArray = if (options.url_safe) ENCODE else ENCODE_WEBSAFE

    private val tail = ByteArray(2)
    private var tailLen = 0
    private var count = if (options.do_newline) LINE_GROUPS else -1

    /**
     * @return an overestimate for the number of bytes `len` bytes could encode to.
     */
    override fun maxOutputSize(len: Int): Int {
        return len * 8 / 5 + 10
    }

    override fun process(input: ByteArray, offset: Int, len: Int, finish: Boolean): Boolean {
        var end = len
        // Using local variables makes the encoder about 9% faster.
        val alphabet = this.alphabet
        val output = this.output
        var op = 0
        var count = this.count

        var p = offset
        end += offset
        var v = -1

        // First we need to concatenate the tail of the previous call
        // with any input bytes available now and see if we can empty
        // the tail.

        when (tailLen) {
            0 -> {
            }

            1 -> {
                if (p + 2 <= end) {
                    // A 1-byte tail with at least 2 bytes of
                    // input available now.
                    v = tail[0].toInt() and 0xff shl 16 or
                        (input[p++].toInt() and 0xff shl 8) or
                        (input[p++].toInt() and 0xff)
                    tailLen = 0
                }
            }

            2 -> if (p + 1 <= end) {
                // A 2-byte tail with at least 1 byte of input.
                v = tail[0].toInt() and 0xff shl 16 or
                    (tail[1].toInt() and 0xff shl 8) or
                    (input[p++].toInt() and 0xff)
                tailLen = 0
            }
        }// There was no tail.

        if (v != -1) {
            output[op++] = alphabet[v shr 18 and 0x3f]
            output[op++] = alphabet[v shr 12 and 0x3f]
            output[op++] = alphabet[v shr 6 and 0x3f]
            output[op++] = alphabet[v and 0x3f]
            if (--count == 0) {
                if (options.do_cr) output[op++] = CR
                output[op++] = NL
                count = LINE_GROUPS
            }
        }

        // At this point either there is no tail, or there are fewer
        // than 3 bytes of input available.

        // The main loop, turning 3 input bytes into 4 output bytes on
        // each iteration.
        while (p + 3 <= end) {
            v = input[p].toInt() and 0xff shl 16 or
                (input[p + 1].toInt() and 0xff shl 8) or
                (input[p + 2].toInt() and 0xff)
            output[op] = alphabet[v shr 18 and 0x3f]
            output[op + 1] = alphabet[v shr 12 and 0x3f]
            output[op + 2] = alphabet[v shr 6 and 0x3f]
            output[op + 3] = alphabet[v and 0x3f]
            p += 3
            op += 4
            if (--count == 0) {
                if (options.do_cr) output[op++] = CR
                output[op++] = NL
                count = LINE_GROUPS
            }
        }

        if (finish) {
            // Finish up the tail of the input.  Note that we need to
            // consume any bytes in tail before any bytes
            // remaining in input; there should be at most two bytes
            // total.

            if (p - tailLen == end - 1) {
                var t = 0
                v = (if (tailLen > 0) tail[t++] else input[p++]).toInt() and 0xff shl 4
                tailLen -= t
                output[op++] = alphabet[v shr 6 and 0x3f]
                output[op++] = alphabet[v and 0x3f]
                if (options.do_padding) {
                    output[op++] = PAD
                    output[op++] = PAD
                }
                if (options.do_newline) {
                    if (options.do_cr) output[op++] = CR
                    output[op++] = NL
                }
            } else if (p - tailLen == end - 2) {
                var t = 0
                v = (if (tailLen > 1) tail[t++] else input[p++]).toInt() and 0xff shl 10 or ((if (tailLen > 0) tail[t++] else input[p++]).toInt() and 0xff shl 2)
                tailLen -= t
                output[op++] = alphabet[v shr 12 and 0x3f]
                output[op++] = alphabet[v shr 6 and 0x3f]
                output[op++] = alphabet[v and 0x3f]
                if (options.do_padding) {
                    output[op++] = PAD
                }
                if (options.do_newline) {
                    if (options.do_cr) output[op++] = CR
                    output[op++] = NL
                }
            } else if (options.do_newline && op > 0 && count != LINE_GROUPS) {
                if (options.do_cr) output[op++] = CR
                output[op++] = NL
            }

            assert(tailLen == 0)
            assert(p == end)
        } else {
            // Save the leftovers in tail to be consumed on the next
            // call to encodeInternal.

            if (p == end - 1) {
                tail[tailLen++] = input[p]
            } else if (p == end - 2) {
                tail[tailLen++] = input[p]
                tail[tailLen++] = input[p + 1]
            }
        }

        this.op = op
        this.count = count

        return true
    }

    companion object {
        private const val PAD = '='.toByte()
        private const val CR = '\r'.toByte()
        private const val NL = '\n'.toByte()
        /**
         * Emit a new line every this many output tuples.  Corresponds to
         * a 76-character line length (the maximum allowable according to
         * [RFC 2045](http://www.ietf.org/rfc/rfc2045.txt)).
         */
        val LINE_GROUPS = 19

        /**
         * Lookup table for turning Base64 alphabet positions (6 bits)
         * into output bytes.
         */
        private val ENCODE = charArrayOf(
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
        ).map { it.toByte() }.toByteArray()

        /**
         * Lookup table for turning Base64 alphabet positions (6 bits)
         * into output bytes.
         */
        private val ENCODE_WEBSAFE = charArrayOf(
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
        ).map { it.toByte() }.toByteArray()
    }
}
