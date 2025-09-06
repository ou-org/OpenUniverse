/*
 * The MIT License
 * Copyright © 2025 OpenUniverse
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.ou.common.constants;

/**
 * <p>
 * IRecordConst interface.</p>
 *

 * @since 1.0.21
 */
public interface IRecordConst {

    String RECORD_ID_KEY = "id";
    String RECORD_TIMESTAMP_KEY = "timestamp";
    String RECORD_SERIAL_NO_KEY = "serial_number";
    String RECORD_TYPE_KEY = "type";
    String RECORD_TYPE_VALUE_TRIGGER = "trigger";
    String RECORD_TYPE_VALUE_PROCESSOR = "processor";
    String OUT_RECORD_RECORD_FORMAT_HASH_KEY = "_record_format_hash_i";
    String OUT_RECORD_RECORD_SIGNATURE_KEY = "_record_signature_s";
    String OUT_RECORD_RECORD_SIGNATURE_ALG_KEY = "_record_signature_alg_s";
    String OUT_RECORD_HASH_KEY = "record_hash_s";
    String OUT_RECORD_CHAIN_HASH_KEY = "record_chain_hash_s";
}
