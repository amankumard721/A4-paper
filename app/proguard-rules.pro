-keepattributes Signature,InnerClasses,EnclosingMethod
# PDFBox's optional JPEG-2000 codec is unused: page rendering uses Android PdfRenderer,
# and text/page import preserves image streams without decoding them through PDFBox.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder

# Optional static-analysis annotation referenced by the Meta SDK.
-dontwarn com.facebook.infer.annotation.Nullsafe
